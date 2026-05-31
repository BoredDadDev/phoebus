/*******************************************************************************
 * Copyright (c) 2015-2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation.javafx.widgets;

import java.awt.Rectangle;

import org.csstudio.display.builder.model.widgets.ThermometerWidget;
import org.csstudio.display.builder.representation.javafx.JFXUtil;

import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.VLineTo;

/** Creates JavaFX item for the Thermometer widget using
 *  {@link org.csstudio.javafx.rtplot.RTTank} as the rendering engine for
 *  the vertical fill column.
 *
 *  <p>Visual design mirrors the original {@link ThermometerRepresentation}:
 *  a single {@link Path} draws the entire glass tube + bulb outline (two
 *  vertical walls with a rounded top cap and a large {@link ArcTo} sweeping
 *  the bulb at the bottom).  A filled {@link Ellipse} drawn above the
 *  RTTank canvas provides the liquid-colour bulb fill.  The RTTank canvas
 *  sits below both, rendering the numeric scale, tick marks, alarm-limit
 *  lines, and the fill bar (liquid level in the tube).
 *
 *  <p>The bulb is <em>always wider than the tube</em>: its radius is
 *  {@code tube_half_width + bulb_size}, clamped to the available horizontal
 *  widget space so it never overflows.  Set {@code bulb_size = 0} to disable
 *  the bulb and revert to a plain scaled-bar appearance.
 *
 *  <p>The bar column's horizontal position is queried from
 *  {@link org.csstudio.javafx.rtplot.RTTank#getBarColumnBounds()} so the
 *  tube outline tracks the bar correctly regardless of scale-label width.
 *  On the very first frame (before {@code computeLayout()} has run) the
 *  bounds are (0,0,0,0); the code falls back to full-width and schedules a
 *  second pass that corrects the position after the first render.
 *
 *  <p>Selected when the {@code thermometer_scale_mode} preference is
 *  {@code true}.  When the preference is {@code false} the stock
 *  {@link ThermometerRepresentation} is used instead.
 *
 *  <p>Shared RTTank lifecycle (value / range / alarm-limit updates) lives in
 *  {@link RTScaledWidgetRepresentation}.
 *
 *  @author Amanda Carpenter  (original ThermometerRepresentation shape design)
 *  @author Heredie Delvalle &mdash; CLS  (RTTank-based rendering, scale, bulb)
 */
@SuppressWarnings("nls")
public class RTThermometerRepresentation extends RTScaledWidgetRepresentation<ThermometerWidget>
{
    /** Radius of tube corners at the top cap (px). */
    private static final double CORNER_R = 3.0;

    // ── tube + bulb shape (drawn on top of RTTank canvas) ─────────────────────
    //
    //  Path traces (anti-clockwise from right side of tube at bulb junction):
    //    tubeStart → up tubeRightWall → round topRightCorner → tubeCap →
    //    round topLeftCorner → down tubeLeftWall → ArcTo bulb → (back to start)
    //
    private final MoveTo  tubeStart       = new MoveTo();
    private final VLineTo tubeRightWall   = new VLineTo(CORNER_R);
    private final LineTo  topRightCorner  = new LineTo();
    private final HLineTo tubeCap         = new HLineTo();
    private final LineTo  topLeftCorner   = new LineTo();
    private final VLineTo tubeLeftWall    = new VLineTo();
    private final ArcTo   bulbArc         = new ArcTo();
    private final Path glassTube = new Path(
            tubeStart, tubeRightWall, topRightCorner,
            tubeCap, topLeftCorner, tubeLeftWall, bulbArc);

    /** Filled ellipse at the bulb centre — the liquid inside the bulb. */
    private final Ellipse bulbFill = new Ellipse();

    // ── JFX node ──────────────────────────────────────────────────────────────

    @Override
    public Pane createJFXNode() throws Exception
    {
        // super adds tank (RTTank canvas) as the first child of the Pane
        final Pane pane = super.createJFXNode();

        // bulbFill sits above the canvas so it is visible in the bulb area
        // (below the tank bottom edge) and blends seamlessly with the fill bar.
        bulbFill.setManaged(false);

        // glassTube is stroke-only — it frames the fill bar and bulb without
        // obscuring the scale labels rendered by RTTank.
        glassTube.setManaged(false);
        glassTube.setFill(null);
        glassTube.setStroke(Color.DARKGRAY);
        glassTube.setStrokeWidth(1.5);

        // Large arc sweeps CCW (downward in screen coords) from left wall to
        // right wall, tracing the bottom of the bulb circle.
        bulbArc.setLargeArcFlag(true);
        bulbArc.setSweepFlag(false);

        // Z-order (bottom → top): tank canvas, bulb liquid fill, glass outline
        pane.getChildren().addAll(bulbFill, glassTube);
        return pane;
    }

    // ── orientation ───────────────────────────────────────────────────────────

    @Override
    protected boolean isHorizontal()
    {
        return false;   // thermometer is always vertical
    }

    // ── listeners ─────────────────────────────────────────────────────────────

    @Override
    protected void registerLookListeners()
    {
        model_widget.propWidth().addUntypedPropertyListener(lookListener);
        model_widget.propHeight().addUntypedPropertyListener(lookListener);
        model_widget.propFont().addUntypedPropertyListener(lookListener);
        model_widget.propFillColor().addUntypedPropertyListener(lookListener);
        model_widget.propBackgroundColor().addUntypedPropertyListener(lookListener);
        model_widget.propScaleVisible().addUntypedPropertyListener(lookListener);
        model_widget.propShowMinorTicks().addUntypedPropertyListener(lookListener);
        model_widget.propShowScaleLabels().addUntypedPropertyListener(lookListener);
        model_widget.propOppositeScaleVisible().addUntypedPropertyListener(lookListener);
        model_widget.propPerpendicularTickLabels().addUntypedPropertyListener(lookListener);
        model_widget.propBorderWidth().addUntypedPropertyListener(lookListener);
        model_widget.propLogScale().addUntypedPropertyListener(lookListener);
        model_widget.propFormat().addUntypedPropertyListener(lookListener);
        model_widget.propPrecision().addUntypedPropertyListener(lookListener);
        model_widget.propInnerPadding().addUntypedPropertyListener(lookListener);
        model_widget.propBulbSize().addUntypedPropertyListener(lookListener);
    }

    @Override
    protected void unregisterLookListeners()
    {
        model_widget.propWidth().removePropertyListener(lookListener);
        model_widget.propHeight().removePropertyListener(lookListener);
        model_widget.propFont().removePropertyListener(lookListener);
        model_widget.propFillColor().removePropertyListener(lookListener);
        model_widget.propBackgroundColor().removePropertyListener(lookListener);
        model_widget.propScaleVisible().removePropertyListener(lookListener);
        model_widget.propShowMinorTicks().removePropertyListener(lookListener);
        model_widget.propShowScaleLabels().removePropertyListener(lookListener);
        model_widget.propOppositeScaleVisible().removePropertyListener(lookListener);
        model_widget.propPerpendicularTickLabels().removePropertyListener(lookListener);
        model_widget.propBorderWidth().removePropertyListener(lookListener);
        model_widget.propLogScale().removePropertyListener(lookListener);
        model_widget.propFormat().removePropertyListener(lookListener);
        model_widget.propPrecision().removePropertyListener(lookListener);
        model_widget.propInnerPadding().removePropertyListener(lookListener);
        model_widget.propBulbSize().removePropertyListener(lookListener);
    }

    // ── appearance ────────────────────────────────────────────────────────────

    @Override
    protected void applyLookToTank(final double width, final double height)
    {
        final Color fill = JFXUtil.convert(model_widget.propFillColor().getValue());
        final Color bg   = JFXUtil.convert(model_widget.propBackgroundColor().getValue());

        // ── Standard RTTank settings ─────────────────────────────────────────
        tank.setFont(JFXUtil.convert(model_widget.propFont().getValue()));
        tank.setFillColor(fill);
        tank.setBackground(bg);
        tank.setEmptyColor(bg);
        tank.setScaleVisible(model_widget.propScaleVisible().getValue());
        tank.setShowMinorTicks(model_widget.propShowMinorTicks().getValue());
        tank.setScaleLabelsVisible(model_widget.propShowScaleLabels().getValue());
        tank.setRightScaleVisible(model_widget.propOppositeScaleVisible().getValue());
        tank.setPerpendicularTickLabels(model_widget.propPerpendicularTickLabels().getValue());
        tank.setBorderWidth(model_widget.propBorderWidth().getValue());
        tank.setLogScale(model_widget.propLogScale().getValue());
        tank.setLabelFormat(model_widget.propFormat().getValue(),
                            model_widget.propPrecision().getValue());
        tank.setInnerPadding(model_widget.propInnerPadding().getValue());

        // ── Bulb + glass tube overlay ────────────────────────────────────────
        final int extraR = model_widget.propBulbSize().getValue();
        if (extraR > 0)
        {
            // Query bar column position from last render pass.
            // Returns (0,0,0,0) before the tank has rendered for the first time.
            final Rectangle barBounds = tank.getBarColumnBounds();
            final double bx, bw;
            if (barBounds.width > 0)
            {
                bx = barBounds.x;
                bw = barBounds.width;
            }
            else
            {
                // First-frame fallback: treat whole widget as bar column.
                // Re-trigger once RTTank has populated its layout bounds.
                bx = 0.0;
                bw = width;
                Platform.runLater(() -> { dirty_look.mark(); toolkit.scheduleUpdate(this); });
            }

            final double cx    = bx + bw / 2.0;      // bar column centre X
            final double tw    = bw / 2.0;             // tube half-width

            // Bulb radius = tube half-width + user-configured extra.
            // Clamped so the bulb never overflows the widget bounds.
            final double br    = Math.min(tw + extraR, Math.min(cx, width - cx));

            // Centre of bulb (br pixels up from widget bottom).
            final double bulbCY = Math.max(br, height - br);

            // Left / right inner edges of the glass tube.
            final double x1 = cx - tw;
            final double x2 = cx + tw;

            // Clip RTTank to the tube area only — the bulb extends below.
            tank.setHeight(Math.max(1.0, bulbCY));

            // ── Bulb fill (liquid colour with radial highlight) ───────────────
            // Drawn above the RTTank canvas so it is always visible in the
            // bulb area; blend is seamless because the fill colour matches.
            bulbFill.setCenterX(cx);
            bulbFill.setCenterY(bulbCY);
            bulbFill.setRadiusX(br - 1.5);
            bulbFill.setRadiusY(br - 1.5);
            bulbFill.setFill(new RadialGradient(
                    0, 0, 0.3, 0.1, 0.4, true, CycleMethod.NO_CYCLE,
                    new Stop(0, fill.interpolate(Color.WHITESMOKE, 0.7)),
                    new Stop(1, fill)));
            bulbFill.setVisible(true);

            // ── Glass tube Path (stroke-only) ─────────────────────────────────
            // Traces from the right tube wall at the bulb junction, up the right
            // wall, across the rounded top cap, down the left wall, then sweeps
            // the bulb arc back to the start — identical topology to the original
            // ThermometerRepresentation border Path.
            tubeStart.setX(x2);
            tubeStart.setY(bulbCY);
            tubeRightWall.setY(CORNER_R);        // up right wall
            topRightCorner.setX(x2 - CORNER_R);
            topRightCorner.setY(0.0);
            tubeCap.setX(x1 + CORNER_R);         // top horizontal cap
            topLeftCorner.setX(x1);
            topLeftCorner.setY(CORNER_R);
            tubeLeftWall.setY(bulbCY);           // down left wall to bulb junction
            // Arc from (x1, bulbCY) sweeping downward to (x2, bulbCY)
            bulbArc.setX(x2);
            bulbArc.setY(bulbCY);
            bulbArc.setRadiusX(br);
            bulbArc.setRadiusY(br);
            glassTube.setVisible(true);
        }
        else
        {
            bulbFill.setVisible(false);
            glassTube.setVisible(false);
        }
    }
}
