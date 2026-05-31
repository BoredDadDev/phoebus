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

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.StrokeType;

/** Creates JavaFX item for the Thermometer widget using
 *  {@link org.csstudio.javafx.rtplot.RTTank} as the rendering engine for
 *  the vertical fill column.
 *
 *  <p>This representation adds a numeric scale, tick format / precision, an
 *  optional second scale, and alarm-limit lines to the thermometer tube.
 *  An optional circular bulb drawn at the bottom of the tube visually
 *  distinguishes the widget from a plain Tank or ProgressBar; its radius is
 *  set by the {@code bulb_size} property (0 = no bulb, default 20 px).
 *
 *  <p>Geometry: when bulb_size &gt; 0 the RTTank occupies
 *  {@code width × (height − bulb_size)} starting at the top, and the bulb
 *  is centred at {@code (width/2, height − bulb_size)}.  The bottom of the
 *  fill column reaches down to the bulb's centre, so the fill visually
 *  flows into the bulb, exactly matching the original hand-drawn look.
 *
 *  <p>Selected when the {@code thermometer_scale_mode} preference is
 *  {@code true}.  When the preference is {@code false} the stock
 *  {@link ThermometerRepresentation} is used instead, preserving the original
 *  hand-drawn thermometer look.
 *
 *  <p>Shared RTTank lifecycle (value / range updates, alarm limits) lives in
 *  {@link RTScaledWidgetRepresentation}.
 *
 *  @author Amanda Carpenter
 *  @author Heredie Delvalle &mdash; CLS, RTTank-based rendering, scale support, bulb
 */
@SuppressWarnings("nls")
public class RTThermometerRepresentation extends RTScaledWidgetRepresentation<ThermometerWidget>
{
    /** Circular bulb drawn at the bottom of the tube; hidden when bulb_size == 0. */
    private volatile Ellipse bulb;

    // ── JFX node ─────────────────────────────────────────────────────────────

    @Override
    public Pane createJFXNode() throws Exception
    {
        final Pane pane = super.createJFXNode();   // creates and stores this.tank
        bulb = new Ellipse();
        bulb.setManaged(false);                    // Pane uses absolute coordinates
        bulb.setStrokeType(StrokeType.INSIDE);
        bulb.setStrokeWidth(1);
        bulb.setStroke(Color.DARKGRAY);
        pane.getChildren().add(bulb);              // bulb drawn on top of tank
        return pane;
    }

    // ── orientation ──────────────────────────────────────────────────────────

    /** Thermometer is always vertical — no orientation toggle. */
    @Override
    protected boolean isHorizontal()
    {
        return false;
    }

    // No configureTank() override: keep RTTank's default gradient fill,
    // which gives the vertical tube a pleasant 3-D depth appearance.

    // ── listeners ────────────────────────────────────────────────────────────

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

        // ── bulb ─────────────────────────────────────────────────────────────
        final int r = model_widget.propBulbSize().getValue();
        if (r > 0)
        {
            // Shrink the RTTank vertically so the tube ends where the bulb begins.
            // The base class already called tank.setHeight(height); we override here.
            tank.setHeight(Math.max(1.0, height - r));

            // Position bulb: centred horizontally, bottom of widget.
            bulb.setCenterX(width / 2.0);
            bulb.setCenterY(height - r);
            bulb.setRadiusX(r);
            bulb.setRadiusY(r);

            // Radial gradient replicates the original ThermometerRepresentation look.
            bulb.setFill(new RadialGradient(0, 0, 0.3, 0.1, 0.4, true, CycleMethod.NO_CYCLE,
                    new Stop(0, fill.interpolate(Color.WHITESMOKE, 0.8)),
                    new Stop(1, fill)));
            bulb.setVisible(true);
        }
        else
        {
            bulb.setVisible(false);
        }

        // ── RTTank appearance ─────────────────────────────────────────────────
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
    }
}
