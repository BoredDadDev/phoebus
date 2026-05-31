/*******************************************************************************
 * Copyright (c) 2014-2023 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.javafx.rtplot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.csstudio.javafx.rtplot.internal.LinearTicks;
import org.csstudio.javafx.rtplot.internal.PlotPart;
import org.csstudio.javafx.rtplot.internal.PlotPartListener;
import org.csstudio.javafx.rtplot.internal.YAxisImpl;
import org.csstudio.javafx.rtplot.internal.util.GraphicsUtils;
import org.phoebus.ui.javafx.BufferUtil;
import org.phoebus.ui.javafx.UpdateThrottle;
import org.phoebus.ui.vtype.ScaleFormat;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.text.Font;

/** Tank with scale
 *
 *  <p>Renders a vertical tank with fill level, optional left and right
 *  scales, a foreground outline, and optional alarm/warning limit lines.
 *  The dual-scale layout is modelled after CS-Studio BOY's tank widget
 *  which supported markers on both sides of the bar.
 *
 *  <p>When alarm limits come from a live PV they are drawn as solid
 *  lines; when they are manually configured the lines are drawn dashed
 *  so the operator can tell at a glance that they are not tied to the
 *  control system's alarm state.
 *
 *  @author Kay Kasemir
 *  @author Heredie Delvalle &mdash; CLS, dual scale, alarm limits,
 *          format/precision, outline, log scale fixes
 */
@SuppressWarnings("nls")
public class RTTank extends Canvas
{
    /** Area of this canvas */
    protected volatile Rectangle area = new Rectangle(0, 0, 0, 0);

    /** Background color */
    private volatile Color background = Color.WHITE;

    /** Foreground color (used for scale ticks and the tank outline) */
    private volatile Color foreground = Color.BLACK;

    /** Fill color */
    private volatile Color empty = Color.LIGHT_GRAY.brighter().brighter();
    private volatile Color empty_shadow = Color.LIGHT_GRAY;

    /** Fill color.
     *  {@code fill_highlight} is a lighter variant used for a gradient.
     */
    private volatile Color fill = Color.BLUE;
    private volatile Color fill_highlight = new Color(72, 72, 255);

    /** Configurable alarm/warning line colors (default: named MAJOR=red, MINOR=orange) */
    private volatile Color limit_major_color = Color.RED;
    private volatile Color limit_minor_color = new Color(255, 128, 0);

    /** Alarm and warning limits drawn as horizontal lines (NaN = not set/hidden) */
    private volatile double limit_lolo = Double.NaN;
    private volatile double limit_lo   = Double.NaN;
    private volatile double limit_hi   = Double.NaN;
    private volatile double limit_hihi = Double.NaN;

    /** Are the alarm limits sourced from PV metadata? When false, use dashed lines. */
    private volatile boolean limits_from_pv = true;

    /** Is the right-side scale displayed? */
    private volatile boolean right_scale_visible = false;

    /** Border width in pixels around the tank body; 0 = no border (default) */
    private volatile int border_width = 0;

    /** Extra inset from canvas edge to plot body on all four sides.
     *  0 = default tank look; positive values give a recessed bar-track
     *  appearance suitable for progress-bar use without a visible scale. */
    private volatile int inner_padding = 0;

    /** When {@code true}, the empty (unfilled) portion of the tank is painted
     *  with a solid colour instead of the default left-to-center gradient.
     *  Use for progress-bar style tracks where the JFX original has a flat
     *  background and only the filled bar carries a visual gradient. */
    private volatile boolean flat_track = false;

    /** When {@code true}, the bar is rendered as a thermometer: a narrow
     *  capillary tube centred in the plot area with a wider liquid-filled bulb
     *  at the bottom, instead of the full-width rounded fill rectangle.  The
     *  scale, alarm lines and value mapping are shared with the tank look so no
     *  rendering logic is duplicated. */
    private volatile boolean thermometer_style = false;

    /** Requested bulb size in pixels (additional bulb diameter beyond the tube
     *  width).  Only used when {@link #thermometer_style} is {@code true}; the
     *  effective bulb is clamped to fit the plot area width and height. */
    private volatile int bulb_size = 20;

    /** Immutable thermometer geometry, computed once by
     *  {@link #computeThermoLayout} and reused by {@link #drawThermometer}
     *  so the scale, tube, bulb and liquid always share one coordinate frame. */
    private static final class ThermoGeom
    {
        final double cx, halfTube, tubeW, xL, xR, tubeTop, yj, bulbCy, bulbR, bulbD;
        ThermoGeom(final double cx, final double halfTube, final double tubeW,
                   final double xL, final double xR, final double tubeTop,
                   final double yj, final double bulbCy, final double bulbR,
                   final double bulbD)
        {
            this.cx = cx; this.halfTube = halfTube; this.tubeW = tubeW;
            this.xL = xL; this.xR = xR; this.tubeTop = tubeTop; this.yj = yj;
            this.bulbCy = bulbCy; this.bulbR = bulbR; this.bulbD = bulbD;
        }
    }

    /** Cached thermometer geometry from the most recent {@link #computeThermoLayout}. */
    private volatile ThermoGeom thermo_geom = null;


    /** Current value, i.e. fill level */
    private volatile double value = 5.0;

    /** Does layout need to be re-computed? */
    protected final AtomicBoolean need_layout = new AtomicBoolean(true);

    /** Throttle updates, enforcing a 'dormant' period */
    private final UpdateThrottle update_throttle;

    /** Buffer for image of the tank and scale */
    private volatile Image plot_image = null;

    /** Is the scale displayed or not. */
    private volatile boolean scale_visible = true;

    protected final AtomicBoolean needUpdate = new AtomicBoolean(true);

    /** Listener to {@link PlotPart}s, triggering refresh of canvas */
    protected final PlotPartListener plot_part_listener = new PlotPartListener()
    {
        @Override
        public void layoutPlotPart(final PlotPart plotPart)
        {
            need_layout.set(true);
        }

        @Override
        public void refreshPlotPart(final PlotPart plotPart)
        {
            requestUpdate();
        }
    };

    /** Redraw the canvas on UI thread by painting the 'plot_image' */
    final private Runnable redraw_runnable = () ->
    {
        final GraphicsContext gc = getGraphicsContext2D();
        final Image image = plot_image;
        if (image != null)
            synchronized (image)
            {
                // Clear the canvas for e.g. transparent pixels (otherwise image appears drawn over previous)
                gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
                // Draw the updated image
                gc.drawImage(image, 0, 0);
            }
    };

    /** Left-side scale axis */
    final private YAxisImpl<Double> scale = new YAxisImpl<>("", plot_part_listener);

    /** Right-side (opposite) scale axis.
     *  Always has {@code is_right=true} and {@code force_text_up=true}
     *  so that axis labels read in the same direction as the left scale.
     */
    final private YAxisImpl<Double> right_scale = new YAxisImpl<>("", plot_part_listener);

    final private PlotPart plot_area = new PlotPart("main", plot_part_listener);



    /** Constructor */
    public RTTank()
    {
        final ChangeListener<? super Number> resize_listener = (prop, old, value) ->
        {
            area = new Rectangle((int)getWidth(), (int)getHeight());
            need_layout.set(true);
            requestUpdate();
        };
        widthProperty().addListener(resize_listener);
        heightProperty().addListener(resize_listener);

        // 20Hz default throttle.
        // When parallel_rendering is enabled, each tank renders on the shared thread pool
        // so that many tanks on a display update concurrently.  The default (false) serialises
        // all renders on the single global UpdateThrottle.TIMER thread — safe but slow for
        // displays with many Tank / ProgressBar widgets.
        update_throttle = new UpdateThrottle(50, TimeUnit.MILLISECONDS, () ->
        {
            if (needUpdate.getAndSet(false)){
                plot_image = updateImageBuffer(); // This will return null if image buffer instantiation times out
                if(plot_image != null){
                    redrawSafely();
                }
                else{
                    requestUpdate();
                }
            }
        }, Activator.parallel_rendering ? Activator.thread_pool : UpdateThrottle.TIMER);

        // Configure right-side scale — must happen after update_throttle is
        // initialised because setOnRight() triggers requestUpdate() via the
        // PlotPartListener.  setForceTextUp(true) keeps the label direction
        // consistent with the left scale.
        right_scale.setOnRight(true);
        right_scale.setForceTextUp(true);
    }


    /** Update the dormant time between updates
     *  @param dormant_time How long throttle remains dormant after a trigger
     *  @param unit Units for the dormant period
     */
    public void setUpdateThrottle(final long dormant_time, final TimeUnit unit)
    {
        update_throttle.setDormantTime(dormant_time, unit);
    }

    /** @param font Scale font */
    public void setFont(final Font font)
    {
        scale.setLabelFont(font);
        scale.setScaleFont(font);
        right_scale.setLabelFont(font);
        right_scale.setScaleFont(font);
    }

    /** @param width Border width in pixels around the tank body (0 = no border, max = 5) */
    public void setBorderWidth(final int width)
    {
        border_width = Math.max(0, Math.min(5, width));
        requestUpdate();
    }

    /** Extra inset from all four canvas edges to the plot body.
     *  Set to 0 (default) for the standard tank look.  Set to a positive
     *  value (e.g. 4) when using RTTank as a progress-bar track so that
     *  the filled area is visually inset from the widget boundary,
     *  matching the margin of the JFX {@code ProgressBar} CSS style.
     *  @param pixels padding in pixels; clamped to [0, 20] */
    public void setInnerPadding(final int pixels)
    {
        inner_padding = Math.max(0, Math.min(20, pixels));
        need_layout.set(true);
        requestUpdate();
    }

    /** Control whether the unfilled track region is painted with a solid colour
     *  ({@code true}) or the default left-to-center gradient ({@code false}).
     *  Enable for progress-bar use to match the flat track background of the
     *  JFX {@code ProgressBar}; disable (default) to keep the Tank look.
     *  @param flat {@code true} = solid track, {@code false} = gradient track */
    public void setFlatTrack(final boolean flat)
    {
        flat_track = flat;
        requestUpdate();
    }

    /** Switch between the standard tank look and the thermometer look.
     *  In thermometer mode the fill is drawn as a narrow capillary tube with a
     *  wider liquid bulb at the bottom (see {@link #drawThermometer}); the
     *  scale, value mapping and alarm-limit handling are unchanged.
     *  @param thermometer {@code true} = thermometer, {@code false} (default) = tank */
    public void setThermometerStyle(final boolean thermometer)
    {
        thermometer_style = thermometer;
        requestUpdate();
    }

    /** Set the bulb size (extra bulb diameter beyond the tube width, in pixels).
     *  Only affects the {@linkplain #setThermometerStyle thermometer} look; the
     *  value is clamped at render time so the bulb always fits the plot area.
     *  @param pixels requested bulb size; negative values are treated as 0 */
    public void setBulbSize(final int pixels)
    {
        bulb_size = Math.max(0, pixels);
        requestUpdate();
    }

    /** Show or hide tick label text on the scale while keeping tick marks visible.
     *  <p>Use this to stack multiple scaled widgets with a single labelled scale:
     *  only the first widget shows text; the rest show aligned tick marks only,
     *  saving horizontal (or vertical) space without losing alignment cues.
     *  <p>Layout and repaint are triggered automatically by the axis when
     *  the value actually changes; no-op when unchanged.
     *  @param visible {@code true} (default) = labels shown; {@code false} = ticks only */
    public void setScaleLabelsVisible(final boolean visible)
    {
        // Each axis fires its own requestLayout()/requestRefresh() via plot_part_listener
        // when the state changes, propagating need_layout and requestUpdate automatically.
        scale.setScaleLabelsVisible(visible);
        right_scale.setScaleLabelsVisible(visible);
    }

    /** @param color Background color */
    public void setBackground(final javafx.scene.paint.Color color)
    {
        background = GraphicsUtils.convert(Objects.requireNonNull(color));
    }

    /** @param color Foreground color (scale ticks and tank outline) */
    public void setForeground(final javafx.scene.paint.Color color)
    {
        foreground = GraphicsUtils.convert(Objects.requireNonNull(color));
        scale.setColor(color);
        right_scale.setColor(color);
    }

    /** @param color Color for empty region */
    public void setEmptyColor(final javafx.scene.paint.Color color)
    {
        empty = GraphicsUtils.convert(Objects.requireNonNull(color));
        empty_shadow = new Color(
                Math.max(0, empty.getRed()   - 32),
                Math.max(0, empty.getGreen() - 32),
                Math.max(0, empty.getBlue()  - 32),
                empty.getAlpha()
            );
    }

    /** @param color Color for filled region */
    public void setFillColor(final javafx.scene.paint.Color color)
    {
        fill = GraphicsUtils.convert(Objects.requireNonNull(color));
        final int saturationContribution = (int) ( 48.f * Color.RGBtoHSB(fill.getRed(), fill.getGreen(), fill.getBlue(), null)[1] );
        fill_highlight = new Color(
            Math.min(255, fill.getRed()   + 32 + saturationContribution),
            Math.min(255, fill.getGreen() + 32 + saturationContribution),
            Math.min(255, fill.getBlue()  + 32 + saturationContribution),
            empty.getAlpha()
        );
    }

    /** @param visible Whether the left-side scale must be displayed. */
    public void setScaleVisible (boolean visible)
    {
        if (visible != scale_visible)
        {
            scale_visible = visible;
            need_layout.set(true);
        }
    }

    /** @param logscale Use log scale for y-axis? */
    public void setLogScale(final boolean logscale)
    {
        scale.setLogarithmic(logscale);
        right_scale.setLogarithmic(logscale);
        requestUpdate();
    }

    /** @param show Show minor tick marks on the scale? */
    public void setShowMinorTicks(final boolean show)
    {
        scale.setShowMinorTicks(show);
        right_scale.setShowMinorTicks(show);
        requestUpdate();
    }

    /** Configure the number format used for scale tick labels.
     *  @param format    Display format; {@code null} or {@link ScaleFormat#DEFAULT} restores automatic formatting.
     *  @param precision Number of decimal places; clamped to [0, 15].
     */
    public void setLabelFormat(final ScaleFormat format, final int precision)
    {
        final int prec = Math.max(0, Math.min(15, precision));
        final NumberFormat fmt;
        if (format == null  ||  format == ScaleFormat.DEFAULT)
            fmt = null;
        else switch (format)
        {
        case DECIMAL:
            fmt = LinearTicks.createDecimalFormat(prec);
            break;
        case EXPONENTIAL:
            fmt = LinearTicks.createExponentialFormat(prec);
            break;
        case ENGINEERING:
            // Engineering format constrains the exponent to multiples of 3.
            // RTTank places ticks via the axis algorithm which does not guarantee
            // that constraint, so this is a best-effort approximation using
            // exponential notation with the requested precision.
            fmt = LinearTicks.createExponentialFormat(prec);
            break;
        case COMPACT:
            // COMPACT picks decimal or exponential per value depending on magnitude.
            // A scale axis applies one NumberFormat to all tick labels, so per-value
            // switching cannot be expressed as a single static format.
            // Fall back to automatic formatting, which already chooses a compact
            // representation based on the axis range.
            fmt = null;
            break;
        case SIGNIFICANT:
            fmt = significantDigitsFormat(prec);
            break;
        default:
            fmt = null;
            break;
        }
        scale.setLabelFormat(fmt);
        right_scale.setLabelFormat(fmt);
        requestUpdate();
    }

    /** Build a {@link NumberFormat} that formats each value to {@code prec} significant
     *  figures using {@code %g}-style notation (decimal or scientific per value magnitude).
     */
    private static NumberFormat significantDigitsFormat(final int prec)
    {
        final String pattern = "%." + prec + "g";
        return new NumberFormat()
        {
            @Override
            public StringBuffer format(final double v, final StringBuffer buf, final java.text.FieldPosition pos)
            {
                return buf.append(normaliseExponent(String.format(java.util.Locale.ROOT, pattern, v)));
            }
            @Override
            public StringBuffer format(final long v, final StringBuffer buf, final java.text.FieldPosition pos)
            {
                return buf.append(normaliseExponent(String.format(java.util.Locale.ROOT, pattern, (double) v)));
            }
            @Override
            public Number parse(final String s, final java.text.ParsePosition pos)
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** Pre-compiled pattern for stripping the sign and leading zeros from a
     *  {@code %g} exponent string such as {@code "-01"} or {@code "+02"}.
     */
    private static final Pattern EXP_LEADING_ZEROS = Pattern.compile("^[+-]?0*");

    /** Normalise a {@code %g}-formatted string to match Phoebus axis convention:
     *  uppercase {@code E}, no leading zeros on the exponent, no {@code +} sign.
     *  Examples: {@code "1.0e-01"} &rarr; {@code "1.0E-1"},
     *            {@code "2.5e+02"} &rarr; {@code "2.5E2"}.
     */
    private static String normaliseExponent(final String s)
    {
        final int e = s.indexOf('e');
        if (e < 0)
            return s;   // decimal notation — no exponent to fix
        final String mantissa = s.substring(0, e);
        final String raw = s.substring(e + 1);      // e.g. "-01", "+02"
        final boolean neg = raw.startsWith("-");
        final String digits = EXP_LEADING_ZEROS.matcher(raw).replaceFirst("");
        return mantissa + "E" + (neg ? "-" : "") + (digits.isEmpty() ? "0" : digits);
    }

    /** Set alarm and warning limit values to display as horizontal lines on the tank.
     *  Pass {@link Double#NaN} for any limit that should not be shown.
     *  @param lolo LOLO (major alarm) lower limit
     *  @param lo   LO   (minor warning) lower limit
     *  @param hi   HI   (minor warning) upper limit
     *  @param hihi HIHI (major alarm) upper limit
     */
    public void setLimits(final double lolo, final double lo,
                          final double hi,   final double hihi)
    {
        limit_lolo = lolo;
        limit_lo   = lo;
        limit_hi   = hi;
        limit_hihi = hihi;
        requestUpdate();
    }

    /** Set the colors used to draw alarm limit lines.
     *  @param minor color for LO / HI (minor warning) lines
     *  @param major color for LOLO / HIHI (major alarm) lines
     */
    public void setAlarmColors(final javafx.scene.paint.Color minor,
                               final javafx.scene.paint.Color major)
    {
        limit_minor_color = GraphicsUtils.convert(Objects.requireNonNull(minor));
        limit_major_color = GraphicsUtils.convert(Objects.requireNonNull(major));
        requestUpdate();
    }

    /** Indicate whether the current alarm limits come from PV metadata.
     *  When {@code false} the limit lines are drawn dashed to signal that
     *  they are manually configured and do not reflect live alarm state.
     *  @param from_pv {@code true} for solid (PV-sourced), {@code false} for dashed
     */
    public void setLimitsFromPV(final boolean from_pv)
    {
        limits_from_pv = from_pv;
        requestUpdate();
    }

    /** Show or hide a second scale on the opposite (right) side of the tank.
     *  Both scales share the same range, log mode, format and tick settings.
     *  @param visible {@code true} to show, {@code false} to hide (default)
     */
    public void setRightScaleVisible(final boolean visible)
    {
        if (right_scale_visible == visible)
            return;
        right_scale_visible = visible;
        need_layout.set(true);
        requestUpdate();
    }

    /** @param perpendicular Draw tick labels perpendicular to the axis direction?
     *                       When {@code false}, labels are rotated parallel to the axis.
     */
    public void setPerpendicularTickLabels(final boolean perpendicular)
    {
        scale.setPerpendicularTickLabels(perpendicular);
        right_scale.setPerpendicularTickLabels(perpendicular);
        need_layout.set(true);   // scale width changes between rotated and perpendicular modes
        requestUpdate();
    }

    /** Set value range
     *  @param low Lower limit
     *  @param high Upper limit
     */
    public void setRange(final double low, final double high)
    {
        // Guard against NaN, Infinite, or inverted/flat range
        if (!Double.isFinite(low) || !Double.isFinite(high) || low >= high)
            return;
        scale.setValueRange(low, high);
        right_scale.setValueRange(low, high);
    }

    /** @param value Set value */
    public void setValue(final double value)
    {
        if (Double.isFinite(value))
            this.value = value;
        else
            this.value = scale.getValueRange().getLow();
        requestUpdate();
    }

    /** Return the bounds of the bar column within the canvas coordinate space.
     *  The bar column is the actual filled rectangle — it excludes any scale
     *  label areas on the left and/or right side.
     *
     *  <p>This is computed during {@link #computeLayout} and is therefore
     *  available only after the first render pass.  Before the first render
     *  the rectangle is {@code (0,0,0,0)}.
     *
     *  <p>Callers that need to overlay a decoration centred on the bar column
     *  (e.g. a thermometer bulb) should use this to avoid misalignment when a
     *  visible scale shifts the bar column away from the canvas edge.
     *
     *  @return bounds of the bar column in canvas pixels (x, y, width, height)
     */
    public Rectangle getBarColumnBounds()
    {
        return plot_area.getBounds();
    }

    /** Map a value to a Y pixel within the plot bounds (low value at bottom).
     *  Returns -1 when the mapping is undefined (e.g. log scale with non-positive inputs).
     */
    private int valueToY(final Rectangle pb, final double min, final double max,
                         final double v, final boolean logscale)
    {
        final double frac;
        if (logscale)
        {
            if (min <= 0 || max <= 0 || v <= 0)
                return -1;
            frac = Math.log(v / min) / Math.log(max / min);
        }
        else
            frac = (v - min) / (max - min);
        return (int) (pb.y + pb.height * (1.0 - frac));
    }

    /** Draw a single horizontal limit line across the tank area at the given value. */
    private void drawLimitLineAt(final Graphics2D gc, final Rectangle pb,
                                  final double min, final double max,
                                  final double limit, final Color color)
    {
        if (!Double.isFinite(limit) || limit <= min || limit >= max)
            return;
        final int y = valueToY(pb, min, max, limit, scale.isLogarithmic());
        if (y < pb.y || y > pb.y + pb.height)
            return;
        gc.setColor(color);
        gc.drawLine(pb.x, y, pb.x + pb.width, y);
    }

    /** Compute the fill height in pixels for the current value.
     *  Handles both linear and logarithmic scales.
     *
     *  @param plotHeight Pixel height of the plot area
     *  @param min        Scale minimum (&lt; max)
     *  @param max        Scale maximum
     *  @param current    Current PV value
     *  @param logscale   Whether the scale uses log spacing
     *  @return Fill level in pixels: 0 = empty, plotHeight = full
     */
    private static int computeFillLevel(final int plotHeight, final double min, final double max,
                                        final double current, final boolean logscale)
    {
        if (current <= min)
            return 0;
        if (current >= max)
            return plotHeight;
        if (logscale) // by mellguth2, https://github.com/ControlSystemStudio/phoebus/issues/2726
        {   // Refuse to map if any input is non-positive (log undefined)
            if (min <= 0 || max <= 0 || current <= 0)
                return 0;
            return (int) (plotHeight * Math.log(current / min) / Math.log(max / min));
        }
        return (int) (plotHeight * (current - min) / (max - min) + 0.5);
    }

    /** Minimum tube width so the capillary stays visible (px). */
    private static final int THERMO_MIN_TUBE = 6;
    /** Maximum tube width so the tube stays a narrow capillary (px). */
    private static final int THERMO_MAX_TUBE = 20;
    /** Minimum tube height kept above the bulb so it never swallows the tube (px). */
    private static final int THERMO_MIN_TUBE_H = 8;
    /** Bulb is always at least this much wider (diameter) than the tube (px). */
    private static final int THERMO_MIN_RING = 6;

    /** Draw the thermometer look using the geometry computed by
     *  {@link #computeThermoLayout}.  The tube, bulb, liquid and scale all share
     *  that one geometry (cached in {@link #thermo_geom}) and the same {@link
     *  Graphics2D}, so they stay mutually aligned at every widget size.
     *
     *  <p>The drawing is split into ordered phases, each its own helper:
     *  empty tube background, liquid (bulb + column), alarm-limit lines and the
     *  glass outline.  This method only resolves the geometry, derives the two
     *  values the phases share ({@code arc}, {@code fillTop}) and calls them.
     *
     *  @param gc       target graphics
     *  @param pb       plot area (unused for geometry now; kept for signature
     *                  symmetry with the tank renderer)
     *  @param min      range minimum (for limit-line filtering)
     *  @param max      range maximum (for limit-line filtering)
     *  @param current  current value
     *  @param normal   {@code true} when the range is ascending (min &le; max)
     */
    private void drawThermometer(final Graphics2D gc, final Rectangle pb,
                                 final double min, final double max,
                                 final double current, final boolean normal)
    {
        final ThermoGeom g = resolveThermoGeometry(gc);
        if (g == null)
            return;

        final int arc = (int) Math.max(2, g.tubeW * 0.6);
        final double fillTop = thermoFillTop(g, current, normal);

        paintEmptyTube(gc, g, arc);
        paintLiquid(gc, g, arc, fillTop);
        if (normal)
            paintLimitLines(gc, g, min, max);
        paintGlassOutline(gc, g, arc);
    }

    /** Return the cached thermometer geometry, computing it on demand when the
     *  first paint precedes a layout pass.  {@code null} only if no geometry
     *  could be produced (zero-sized area). */
    private ThermoGeom resolveThermoGeometry(final Graphics2D gc)
    {
        ThermoGeom g = thermo_geom;
        if (g == null)
        {
            computeThermoLayout(gc, area);
            g = thermo_geom;
        }
        return g;
    }

    /** Y pixel of the liquid surface, from the scale's own transform so the
     *  surface lines up with the tick marks, clamped into the tube range.
     *  An inverted range fills the whole tube. */
    private double thermoFillTop(final ThermoGeom g, final double current, final boolean normal)
    {
        if (!normal)
            return g.tubeTop;
        return Math.max(g.tubeTop, Math.min(scale.getScreenCoord(current), g.yj));
    }

    /** Paint the empty tube background above the liquid (flat or shaded). */
    private void paintEmptyTube(final Graphics2D gc, final ThermoGeom g, final int arc)
    {
        if (flat_track)
            gc.setColor(empty);
        else
            gc.setPaint(new GradientPaint((float) g.xL, 0, empty,
                                          (float) g.cx, 0, empty_shadow, true));
        gc.fillRoundRect((int) Math.round(g.xL), (int) Math.round(g.tubeTop),
                         (int) Math.round(g.tubeW), (int) Math.round(g.yj - g.tubeTop),
                         arc, arc);
    }

    /** Paint the liquid: the always-full bulb plus the tube column up to
     *  {@code fillTop}. */
    private void paintLiquid(final Graphics2D gc, final ThermoGeom g,
                             final int arc, final double fillTop)
    {
        gc.setPaint(new GradientPaint((float) g.xL, 0, fill,
                                      (float) g.cx, 0, fill_highlight, true));
        gc.fillOval((int) Math.round(g.cx - g.bulbR), (int) Math.round(g.bulbCy - g.bulbR),
                    (int) Math.round(g.bulbD), (int) Math.round(g.bulbD));
        if (fillTop < g.yj)
            gc.fillRoundRect((int) Math.round(g.xL), (int) Math.round(fillTop),
                             (int) Math.round(g.tubeW), (int) Math.round(g.yj - fillTop) + arc,
                             arc, arc);
    }

    /** Paint the alarm / warning limit lines across the tube.  Lines from a PV
     *  are solid; manually configured limits are dashed. */
    private void paintLimitLines(final Graphics2D gc, final ThermoGeom g,
                                 final double min, final double max)
    {
        final double lim_lolo = limit_lolo, lim_lo = limit_lo;
        final double lim_hi = limit_hi, lim_hihi = limit_hihi;
        if (Double.isNaN(lim_lolo) && Double.isNaN(lim_lo) &&
            Double.isNaN(lim_hi)   && Double.isNaN(lim_hihi))
            return;

        if (limits_from_pv)
            gc.setStroke(new BasicStroke(2f));
        else
            gc.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
        drawThermoLimit(gc, min, max, lim_lolo, g, limit_major_color);
        drawThermoLimit(gc, min, max, lim_lo,   g, limit_minor_color);
        drawThermoLimit(gc, min, max, lim_hi,   g, limit_minor_color);
        drawThermoLimit(gc, min, max, lim_hihi, g, limit_major_color);
        gc.setStroke(new BasicStroke(1f));
    }

    /** Paint the glass outline: tube walls, rounded cap and bulb arc.
     *  Honours {@code border_width} literally &mdash; {@code 0} draws nothing. */
    private void paintGlassOutline(final Graphics2D gc, final ThermoGeom g, final int arc)
    {
        if (border_width <= 0)
            return;

        gc.setColor(foreground);
        gc.setStroke(new BasicStroke(border_width));
        final Path2D.Double outline = new Path2D.Double();
        outline.moveTo(g.xL, g.yj);
        outline.lineTo(g.xL, g.tubeTop + arc / 2.0);
        outline.quadTo(g.xL, g.tubeTop, g.xL + arc / 2.0, g.tubeTop);
        outline.lineTo(g.xR - arc / 2.0, g.tubeTop);
        outline.quadTo(g.xR, g.tubeTop, g.xR, g.tubeTop + arc / 2.0);
        outline.lineTo(g.xR, g.yj);
        final double angR = Math.toDegrees(Math.atan2(-(g.yj - g.bulbCy), g.xR - g.cx));
        final double angL = Math.toDegrees(Math.atan2(-(g.yj - g.bulbCy), g.xL - g.cx));
        final Arc2D.Double bulbArc = new Arc2D.Double(g.cx - g.bulbR, g.bulbCy - g.bulbR,
                g.bulbD, g.bulbD, angR, -(360 - (angL - angR)), Arc2D.OPEN);
        outline.append(bulbArc, true);
        outline.closePath();
        gc.draw(outline);
        gc.setStroke(new BasicStroke(1f));
    }

    /** Draw a single horizontal limit line across the tube at the given value.
     *  Uses the scale's own transform so the line aligns with the tick marks. */
    private void drawThermoLimit(final Graphics2D gc,
                                 final double min, final double max, final double limit,
                                 final ThermoGeom g, final Color color)
    {
        if (!Double.isFinite(limit) || limit <= min || limit >= max)
            return;
        final int y = scale.getScreenCoord(limit);
        if (y < g.tubeTop || y > g.yj)
            return;
        gc.setColor(color);
        gc.drawLine((int) Math.round(g.xL), y, (int) Math.round(g.xR), y);
    }

    /** Compute layout of plot components.
     *  Supports independent left and right scales; the plot area sits
     *  between them.  A 1-pixel inset is added on any edge that has no
     *  scale so the outline {@code drawRoundRect} is not clipped.
     */
    private void computeLayout(final Graphics2D gc, final Rectangle bounds)
    {
        if (thermometer_style)
        {
            computeThermoLayout(gc, bounds);
            return;
        }

        int left_width  = 0;
        int right_width = 0;
        int[] ends = { 0, 0 };   // [bottom gap, top gap]

        if (scale_visible)
        {
            left_width = scale.getDesiredPixelSize(bounds, gc);
            ends = scale.getPixelGaps(gc);
        }
        if (right_scale_visible)
        {
            right_width = right_scale.getDesiredPixelSize(bounds, gc);
            final int[] r_ends = right_scale.getPixelGaps(gc);
            ends[0] = Math.max(ends[0], r_ends[0]);
            ends[1] = Math.max(ends[1], r_ends[1]);
        }

        // Inset = ceil(border_width/2) keeps the outer stroke edge inside the canvas.
        // On sides with a scale the label area provides ample margin so inset=0.
        // When there is no border, inset=1 is the original clip guard.
        // inner_padding is added on top of all four sides regardless of scale presence;
        // it is 0 for the standard tank and > 0 for the progress-bar track mode.
        final int half_bw_ceil = (border_width + 1) / 2;
        final int ip = inner_padding;
        final int inset_left   = (left_width  == 0) ? Math.max(1, half_bw_ceil) + ip : ip;
        final int inset_right  = (right_width == 0) ? Math.max(1, half_bw_ceil) + ip : ip;
        final int inset_top    = (ends[1] == 0) ? Math.max(1, half_bw_ceil) + ip : ip;
        final int inset_bottom = (ends[0] == 0) ? Math.max(1, half_bw_ceil) + ip : ip;

        final int top    = bounds.y + ends[1] + inset_top;
        final int height = bounds.height - ends[0] - ends[1] - inset_top - inset_bottom;

        if (scale_visible)
            scale.setBounds(new Rectangle(bounds.x, top, left_width, height));
        if (right_scale_visible)
            right_scale.setBounds(new Rectangle(bounds.x + bounds.width - right_width,
                                                top, right_width, height));

        plot_area.setBounds(bounds.x + left_width + inset_left, top,
                            bounds.width - left_width - right_width - inset_left - inset_right, height);
    }

    /** Thermometer-specific layout.
     *
     *  <p>Unlike the tank layout, each scale is glued to the immediate side of
     *  the narrow tube and spans only the tube's vertical range (top &rarr; bulb
     *  top), never the bulb.  The whole {@code [left scale | gap | tube | gap |
     *  right scale]} assembly, with the wider bulb centred under the tube, is
     *  centred horizontally so it travels together on resize.
     *
     *  <p>Following the classic Phoebus thermometer, the tube and bulb widen
     *  with the widget width up to a cap: {@code tubeW = clamp(thermoW/2, MIN,
     *  MAX)} and {@code bulbD = clamp(tubeW + bulb_size, ...)}.  Beyond the cap
     *  only the surrounding margin grows.  The geometry is cached in
     *  {@link #thermo_geom} for {@link #drawThermometer} so the liquid level
     *  (via {@link YAxisImpl#getScreenCoord}) aligns exactly with the ticks.
     */
    private void computeThermoLayout(final Graphics2D gc, final Rectangle bounds)
    {
        final int ip  = inner_padding;
        final int gap = 3;                 // pixels between scale labels and tube

        final int[] sc = measureThermoScales(gc, bounds);
        final int leftScaleW = sc[0], rightScaleW = sc[1], topGap = sc[2], botGap = sc[3];

        // Vertical extent of the usable area (label overflow reserved via gaps).
        final double top    = bounds.y + ip + topGap + 1.0;
        final double bottom = bounds.y + bounds.height - ip - botGap - 1.0;
        final double availH = Math.max(1.0, bottom - top);

        // Width left for the thermometer once the scales and gaps are reserved.
        final int scaleSpace = leftScaleW + (leftScaleW > 0 ? gap : 0)
                             + rightScaleW + (rightScaleW > 0 ? gap : 0);
        final double thermoW = Math.max(THERMO_MIN_TUBE,
                                        bounds.width - 2.0 * ip - scaleSpace);

        // Tube widens with width (half the thermometer area), capped.  Matches
        // the stock thermometer's w = clamp(width/2, 0, 20).
        final double tubeW = Math.min(THERMO_MAX_TUBE,
                                      Math.max(THERMO_MIN_TUBE, thermoW / 2.0));
        final double halfTube = tubeW / 2.0;

        // Bulb wider than the tube, clamped to fit the remaining width & height.
        double bulbD = tubeW + bulb_size;
        bulbD = Math.min(bulbD, availH - THERMO_MIN_TUBE_H);
        bulbD = Math.min(bulbD, thermoW);
        bulbD = Math.max(bulbD, tubeW + THERMO_MIN_RING);
        final double bulbR = bulbD / 2.0;

        // Centre the assembly.  Each side reaches out by whichever is wider:
        // the scale next to the tube, or the bulb radius.
        final double leftExt  = Math.max(halfTube + (leftScaleW  > 0 ? gap + leftScaleW  : 0), bulbR);
        final double rightExt = Math.max(halfTube + (rightScaleW > 0 ? gap + rightScaleW : 0), bulbR);
        double cx = bounds.x + (bounds.width - (leftExt + rightExt)) / 2.0 + leftExt;
        cx = Math.max(bounds.x + ip + leftExt,
             Math.min(cx, bounds.x + bounds.width - ip - rightExt));

        final double xL = cx - halfTube;
        final double xR = cx + halfTube;
        final double bulbCy = bottom - bulbR;
        final double chord = Math.min(halfTube, bulbR - 0.001);
        final double yj = bulbCy - Math.sqrt(bulbR * bulbR - chord * chord);

        final ThermoGeom g = new ThermoGeom(cx, halfTube, tubeW, xL, xR,
                                            top, yj, bulbCy, bulbR, bulbD);
        placeThermoScales(g, leftScaleW, rightScaleW, gap);

        // plot_area = tube+bulb bounding box (the scale.paint grid reference).
        plot_area.setBounds((int) Math.round(cx - bulbR), (int) Math.round(top),
                            (int) Math.round(bulbD), (int) Math.round(bottom - top));

        thermo_geom = g;
    }

    /** Measure the visible scales' widths and shared vertical label gaps.
     *  @return {@code { leftScaleWidth, rightScaleWidth, topGap, bottomGap }}
     *          in pixels (a width of 0 means that scale is hidden). */
    private int[] measureThermoScales(final Graphics2D gc, final Rectangle bounds)
    {
        int leftW = 0, rightW = 0, topGap = 0, botGap = 0;
        if (scale_visible)
        {
            leftW = scale.getDesiredPixelSize(bounds, gc);
            final int[] gaps = scale.getPixelGaps(gc);
            botGap = Math.max(botGap, gaps[0]);
            topGap = Math.max(topGap, gaps[1]);
        }
        if (right_scale_visible)
        {
            rightW = right_scale.getDesiredPixelSize(bounds, gc);
            final int[] gaps = right_scale.getPixelGaps(gc);
            botGap = Math.max(botGap, gaps[0]);
            topGap = Math.max(topGap, gaps[1]);
        }
        return new int[] { leftW, rightW, topGap, botGap };
    }

    /** Place each visible scale flush against its tube wall, spanning only the
     *  tube's vertical range (top &rarr; bulb top) so it never covers the bulb. */
    private void placeThermoScales(final ThermoGeom g, final int leftScaleW,
                                   final int rightScaleW, final int gap)
    {
        final int sy = (int) Math.round(g.tubeTop);
        final int sh = Math.max(1, (int) Math.round(g.yj - g.tubeTop));
        if (scale_visible)
            scale.setBounds(new Rectangle((int) Math.round(g.xL - gap - leftScaleW),
                                          sy, leftScaleW, sh));
        if (right_scale_visible)
            right_scale.setBounds(new Rectangle((int) Math.round(g.xR + gap),
                                                sy, rightScaleW, sh));
    }


    /** Draw all components into image buffer */
    protected Image updateImageBuffer()
    {
        final Rectangle area_copy = area;
        if (area_copy.width <= 0  ||  area_copy.height <= 0)
            return null;

        final BufferUtil buffer = BufferUtil.getBufferedImage(area_copy.width, area_copy.height);
        if (buffer == null)
            return null;
        final BufferedImage image = buffer.getImage();
        final Graphics2D gc = buffer.getGraphics();

        gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gc.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        gc.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
        gc.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        if (need_layout.getAndSet(false))
            computeLayout(gc, area_copy);

        final Rectangle plot_bounds = plot_area.getBounds();

        gc.setColor(background);
        gc.fillRect(0, 0, area_copy.width, area_copy.height);

        if (scale_visible)
            scale.paint(gc, plot_bounds);
        if (right_scale_visible)
            right_scale.paint(gc, plot_bounds);

        plot_area.paint(gc);

        final AxisRange<Double> range = scale.getValueRange();
        final boolean normal = range.getLow() <= range.getHigh();
        final double min = Math.min(range.getLow(), range.getHigh());
        final double max = Math.max(range.getLow(), range.getHigh());
        final double current = value;

        if (thermometer_style)
        {
            drawThermometer(gc, plot_bounds, min, max, current, normal);
            gc.dispose();
            return SwingFXUtils.toFXImage(image, null);
        }

        final int level = computeFillLevel(plot_bounds.height, min, max, current, scale.isLogarithmic());

        final int arc = Math.min(plot_bounds.width, plot_bounds.height) / 10;
        if (flat_track)
            gc.setColor(empty);
        else
            gc.setPaint(new GradientPaint(plot_bounds.x, 0, empty, plot_bounds.x+plot_bounds.width/2, 0, empty_shadow, true));
        gc.fillRoundRect(plot_bounds.x, plot_bounds.y, plot_bounds.width, plot_bounds.height, arc, arc);

        gc.setPaint(new GradientPaint(plot_bounds.x, 0, fill, plot_bounds.x+plot_bounds.width/2, 0, fill_highlight, true));
        if (normal)
            gc.fillRoundRect(plot_bounds.x, plot_bounds.y+plot_bounds.height-level, plot_bounds.width, level, arc, arc);
        else
            gc.fillRoundRect(plot_bounds.x, plot_bounds.y, plot_bounds.width, level, arc, arc);

        // Optional border: stroked CENTRED on plot_bounds — no integer half-pixel
        // shifting.  The inner half of the stroke covers the fill edge (no gap);
        // the outer half extends beyond plot_bounds into the inset margin.
        // Ticks land at plot_bounds edges = centre of the border stroke, matching
        // the CS-Studio BOY convention.
        if (border_width > 0)
        {
            // Java2D: fillRoundRect covers x..x+w-1, drawRoundRect strokes x..x+w.
            // Using w-1, h-1 aligns the stroke centre with the fill boundary,
            // making all four edges symmetric.
            gc.setColor(foreground);
            gc.setStroke(new BasicStroke(border_width));
            gc.drawRoundRect(plot_bounds.x, plot_bounds.y,
                             plot_bounds.width - 1, plot_bounds.height - 1,
                             arc, arc);
            gc.setStroke(new BasicStroke(1f));
        }

        // Draw alarm / warning limit lines over the tank body
        final double lim_lolo = limit_lolo;
        final double lim_lo   = limit_lo;
        final double lim_hi   = limit_hi;
        final double lim_hihi = limit_hihi;
        if (normal && (!Double.isNaN(lim_lolo) || !Double.isNaN(lim_lo) ||
                        !Double.isNaN(lim_hi)   || !Double.isNaN(lim_hihi)))
        {
            if (limits_from_pv)
                gc.setStroke(new BasicStroke(2f));
            else
                gc.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
            drawLimitLineAt(gc, plot_bounds, min, max, lim_lolo, limit_major_color);
            drawLimitLineAt(gc, plot_bounds, min, max, lim_lo,   limit_minor_color);
            drawLimitLineAt(gc, plot_bounds, min, max, lim_hi,   limit_minor_color);
            drawLimitLineAt(gc, plot_bounds, min, max, lim_hihi, limit_major_color);
            gc.setStroke(new BasicStroke(1f));
        }

        gc.dispose();

        // Convert to JFX
        return SwingFXUtils.toFXImage(image, null);
    }

    /** Request a complete redraw of the plot */
    final public void requestUpdate()
    {
        needUpdate.set(true);
        update_throttle.trigger();
    }

    /** Redraw the current image and cursors
     *  <p>May be called from any thread.
     */
    final void redrawSafely()
    {
        Platform.runLater(redraw_runnable);
    }

    /** Should be invoked when plot no longer used to release resources */
    public void dispose()
    {   // Stop updates which could otherwise still use
        // what's about to be disposed
        update_throttle.dispose();
    }
}
