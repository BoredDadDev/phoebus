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

import javafx.scene.paint.Color;

/** Creates JavaFX item for the Thermometer widget using
 *  {@link org.csstudio.javafx.rtplot.RTTank} as the rendering engine for
 *  the vertical fill column.
 *
 *  <p>This representation adds a numeric scale, tick format / precision, an
 *  optional second scale, and alarm-limit lines to the thermometer tube.  The
 *  decorative bulb is not rendered; the widget becomes a fully-functional
 *  vertical bar display with a gradient fill that evokes a thermometer tube.
 *
 *  <p>Selected when the {@code thermometer_scale_mode} preference is
 *  {@code true}.  When the preference is {@code false} the stock
 *  {@link ThermometerRepresentation} is used instead, preserving the original
 *  hand-drawn thermometer look.
 *
 *  <p>Shared RTTank lifecycle (value / range updates, alarm limits) lives in
 *  {@link RTScaledWidgetRepresentation}.  This class contributes only the
 *  Thermometer-specific appearance: fill colour and gradient tube background.
 *
 *  @author Amanda Carpenter
 *  @author Heredie Delvalle &mdash; CLS, RTTank-based rendering, scale support
 */
@SuppressWarnings("nls")
public class RTThermometerRepresentation extends RTScaledWidgetRepresentation<ThermometerWidget>
{
    /** Thermometer is always vertical — no orientation toggle. */
    @Override
    protected boolean isHorizontal()
    {
        return false;
    }

    // No configureTank() override: keep RTTank's default gradient fill,
    // which gives the vertical tube a pleasant 3-D depth appearance.

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
    }

    @Override
    protected void applyLookToTank(final double width, final double height)
    {
        tank.setFont(JFXUtil.convert(model_widget.propFont().getValue()));
        tank.setFillColor(JFXUtil.convert(model_widget.propFillColor().getValue()));
        final Color bg = JFXUtil.convert(model_widget.propBackgroundColor().getValue());
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
    }
}
