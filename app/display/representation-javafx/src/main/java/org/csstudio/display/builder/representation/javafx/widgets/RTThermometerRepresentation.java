/*******************************************************************************
 * Copyright (c) 2015-2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation.javafx.widgets;

import org.csstudio.display.builder.model.widgets.ThermometerWidget;
import org.csstudio.display.builder.representation.javafx.JFXUtil;

/** Creates the JavaFX item for the Thermometer widget using
 *  {@link org.csstudio.javafx.rtplot.RTTank} in its thermometer rendering mode.
 *
 *  <p>This representation owns <b>no</b> geometry of its own.  All the rendering
 *  &mdash; the numeric scale, tick marks, alarm-limit lines, the capillary tube,
 *  the liquid bulb and the fill level &mdash; is produced by {@link
 *  org.csstudio.javafx.rtplot.RTTank} in a single {@code Graphics2D} pass, so the
 *  tube, the bulb and the scale always share one coordinate system and stay
 *  perfectly aligned at every widget size.  Compared with the standard
 *  {@link ThermometerRepresentation} this variant adds a real, configurable
 *  scale (log / format / precision / minor ticks) and alarm-limit lines.
 *
 *  <p>The shared RTTank lifecycle (value / range / alarm-limit updates,
 *  orientation, listener scheduling) lives in {@link RTScaledWidgetRepresentation};
 *  this class only:
 *  <ul>
 *    <li>enables the thermometer rendering style ({@link #configureTank()}),</li>
 *    <li>registers / unregisters the thermometer's look properties, and</li>
 *    <li>pushes those look properties to the tank ({@link #applyLookToTank}).</li>
 *  </ul>
 *
 *  <p>Selected when the {@code thermometer_scale_mode} preference is
 *  {@code true}; otherwise the stock {@link ThermometerRepresentation} is used.
 *
 *  @author Amanda Carpenter  (original ThermometerRepresentation shape design)
 *  @author Heredie Delvalle - CLS  (RTTank-based rendering, scale, bulb)
 */
@SuppressWarnings("nls")
public class RTThermometerRepresentation extends RTScaledWidgetRepresentation<ThermometerWidget>
{
    // -- orientation -----------------------------------------------------------

    @Override
    protected boolean isHorizontal()
    {
        return false;   // a thermometer is always vertical
    }

    // -- one-time tank setup ---------------------------------------------------

    @Override
    protected void configureTank()
    {
        tank.setThermometerStyle(true);
    }

    // -- listeners -------------------------------------------------------------

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

    // -- appearance ------------------------------------------------------------

    @Override
    protected void applyLookToTank(final double width, final double height)
    {
        tank.setFont(JFXUtil.convert(model_widget.propFont().getValue()));
        tank.setFillColor(JFXUtil.convert(model_widget.propFillColor().getValue()));
        tank.setBackground(JFXUtil.convert(model_widget.propBackgroundColor().getValue()));
        // Empty (unfilled) tube area uses the background color so it blends into the
        // widget background; the liquid fill_color provides the only visible contrast.
        tank.setEmptyColor(JFXUtil.convert(model_widget.propBackgroundColor().getValue()));
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
        tank.setBulbSize(model_widget.propBulbSize().getValue());
    }
}
