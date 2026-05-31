/*******************************************************************************
 * Copyright (c) 2015-2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.widgets;

import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newIntegerPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propBackgroundColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propFillColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propFont;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propBorderWidth;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propOppositeScaleVisible;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propPerpendicularTickLabels;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propScaleVisible;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propShowMinorTicks;
import static org.csstudio.display.builder.model.widgets.ScaledPVWidget.propShowScaleLabels;
import static org.csstudio.display.builder.model.widgets.plots.PlotWidgetProperties.propLogscale;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.csstudio.display.builder.model.Messages;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetCategory;
import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyCategory;
import org.csstudio.display.builder.model.WidgetPropertyDescriptor;
import org.csstudio.display.builder.model.persist.NamedWidgetFonts;
import org.csstudio.display.builder.model.persist.WidgetFontService;
import org.csstudio.display.builder.model.properties.WidgetFont;
import org.phoebus.ui.color.WidgetColor;

/** Widget that displays a thermometer with an optional numeric scale.
 *
 *  <p>Extends {@link ScaledPVWidget} to inherit common scale/limit properties.
 *  When {@code thermometer_scale_mode=true} the vertical tube is rendered by
 *  {@code RTTank} (same engine as TankWidget and ProgressBar in scale mode),
 *  gaining tick marks, scale labels, and alarm-limit lines.
 *
 *  <p>Existing {@code .bob} files load unchanged: {@code fill_color},
 *  {@code limits_from_pv}, {@code minimum} and {@code maximum} keep the same
 *  XML names.  New properties are silently ignored by older Phoebus versions.
 *
 *  @author Amanda Carpenter
 *  @author Heredie Delvalle &mdash; CLS, ScaledPVWidget refactoring, scale support
 */
@SuppressWarnings("nls")
public class ThermometerWidget extends ScaledPVWidget
{
    /** Property names that only take effect when the RTTank-based rendering is
     *  active ({@code thermometer_scale_mode=true}).  The property editor uses
     *  this set to hide irrelevant entries when the legacy renderer is selected. */
    public static final Set<String> SCALE_MODE_PROPS = Set.of(
        "format", "precision",
        "background_color", "font",
        "log_scale",
        "scale_visible", "show_minor_ticks", "show_scale_labels",
        "opposite_scale_visible", "perpendicular_tick_labels",
        "inner_padding", "border_width",
        "bulb_size",
        "alarm_limits_from_pv", "show_alarm_limits",
        "level_lolo", "level_low", "level_high", "level_hihi",
        "minor_alarm_color", "major_alarm_color");

    /** 'inner_padding' — extra inset from widget edge to the fill column, in pixels (0..20). */
    public static final WidgetPropertyDescriptor<Integer> propInnerPadding =
        newIntegerPropertyDescriptor(WidgetPropertyCategory.DISPLAY, "inner_padding",
                                     Messages.WidgetProperties_InnerPadding, 0, 20);

    /** 'bulb_size' — radius in pixels of the circular bulb drawn at the bottom of the
     *  thermometer tube in RTTank scale mode.  Set to 0 to suppress the bulb entirely.
     *  Default 20 matches the auto-size of the stock thermometer for a 40 px wide widget. */
    public static final WidgetPropertyDescriptor<Integer> propBulbSize =
        newIntegerPropertyDescriptor(WidgetPropertyCategory.DISPLAY, "bulb_size",
                                     Messages.WidgetProperties_BulbSize, 0, 50);

    /** Widget descriptor */
    public static final WidgetDescriptor WIDGET_DESCRIPTOR = new WidgetDescriptor("thermometer",
            WidgetCategory.MONITOR,
            "Thermometer",
            "/icons/Thermo.png",
            "A thermometer",
            Arrays.asList("org.csstudio.opibuilder.widgets.thermometer"))
    {
        @Override
        public Widget createWidget()
        {
            return new ThermometerWidget();
        }
    };

    private volatile WidgetProperty<WidgetFont>  font;
    private volatile WidgetProperty<WidgetColor> fill_color;
    private volatile WidgetProperty<WidgetColor> background_color;
    private volatile WidgetProperty<Boolean>     log_scale;
    private volatile WidgetProperty<Boolean>     scale_visible;
    private volatile WidgetProperty<Boolean>     show_minor_ticks;
    private volatile WidgetProperty<Boolean>     show_scale_labels;
    private volatile WidgetProperty<Boolean>     opposite_scale_visible;
    private volatile WidgetProperty<Boolean>     perpendicular_tick_labels;
    private volatile WidgetProperty<Integer>     border_width_prop;
    private volatile WidgetProperty<Integer>     inner_padding_prop;
    private volatile WidgetProperty<Integer>     bulb_size_prop;

    /** Constructor */
    public ThermometerWidget()
    {
        super(WIDGET_DESCRIPTOR.getType(), 40, 160);
    }

    @Override
    protected void defineProperties(final List<WidgetProperty<?>> properties)
    {
        super.defineProperties(properties);
        properties.add(fill_color               = propFillColor.createProperty(this, new WidgetColor(60, 255, 60)));
        properties.add(background_color         = propBackgroundColor.createProperty(this, new WidgetColor(250, 250, 250)));
        properties.add(log_scale                = propLogscale.createProperty(this, false));
        properties.add(scale_visible            = propScaleVisible.createProperty(this, true));
        properties.add(show_minor_ticks         = propShowMinorTicks.createProperty(this, true));
        properties.add(show_scale_labels        = propShowScaleLabels.createProperty(this, true));
        properties.add(opposite_scale_visible   = propOppositeScaleVisible.createProperty(this, false));
        properties.add(perpendicular_tick_labels = propPerpendicularTickLabels.createProperty(this, true));
        properties.add(border_width_prop        = propBorderWidth.createProperty(this, 0));
        properties.add(inner_padding_prop       = propInnerPadding.createProperty(this, 3));
        properties.add(bulb_size_prop           = propBulbSize.createProperty(this, 20));
        properties.add(font                     = propFont.createProperty(this, WidgetFontService.get(NamedWidgetFonts.DEFAULT)));
    }

    /** @return 'font' property */
    public WidgetProperty<WidgetFont> propFont()                      { return font; }

    /** @return 'fill_color' property */
    public WidgetProperty<WidgetColor> propFillColor()                { return fill_color; }

    /** @return 'background_color' property */
    public WidgetProperty<WidgetColor> propBackgroundColor()          { return background_color; }

    /** @return 'log_scale' property */
    public WidgetProperty<Boolean> propLogScale()                     { return log_scale; }

    /** @return 'scale_visible' property */
    public WidgetProperty<Boolean> propScaleVisible()                 { return scale_visible; }

    /** @return 'show_minor_ticks' property */
    public WidgetProperty<Boolean> propShowMinorTicks()               { return show_minor_ticks; }

    /** @return 'show_scale_labels' property */
    public WidgetProperty<Boolean> propShowScaleLabels()              { return show_scale_labels; }

    /** @return 'opposite_scale_visible' property */
    public WidgetProperty<Boolean> propOppositeScaleVisible()         { return opposite_scale_visible; }

    /** @return 'perpendicular_tick_labels' property */
    public WidgetProperty<Boolean> propPerpendicularTickLabels()      { return perpendicular_tick_labels; }

    /** @return 'border_width' property */
    public WidgetProperty<Integer> propBorderWidth()                  { return border_width_prop; }

    /** @return 'inner_padding' property */
    public WidgetProperty<Integer> propInnerPadding()                 { return inner_padding_prop; }

    /** @return 'bulb_size' property — radius in pixels, 0 = no bulb */
    public WidgetProperty<Integer> propBulbSize()                     { return bulb_size_prop; }
}
