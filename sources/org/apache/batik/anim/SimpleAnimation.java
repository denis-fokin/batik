package org.apache.batik.anim;

import java.awt.geom.Point2D;

import org.apache.batik.anim.timing.TimedElement;
import org.apache.batik.anim.values.AnimatableValue;
import org.apache.batik.ext.awt.geom.Cubic;

/**
 * An animation class for 'animate' animations.
 */
public class SimpleAnimation extends AbstractAnimation {

    /**
     * The interpolation mode of this animator.  This should take one
     * of the CALC_MODE_* constants defined in {@link AbstractAnimation}.
     */
    protected int calcMode;

    /**
     * Values between which to interpolate.
     */
    protected AnimatableValue[] values;

    /**
     * Time values to control the pacing of the animation.
     */
    protected float[] keyTimes;

    /**
     * Bezier control points that control the pacing of the animation.
     */
    protected float[] keySplines;

    /**
     * Starting value of the animation.
     */
    protected AnimatableValue from;

    /**
     * Ending value of the animation.
     */
    protected AnimatableValue to;

    /**
     * Relative offset value for the animation.
     */
    protected AnimatableValue by;
    
    /**
     * Whether this animation adds to ones below it in the animation sandwich
     * or replaces them.
     */
    protected boolean additive;

    /**
     * Whether this animation accumulates from previous iterations.
     */
    protected boolean cumulative;

    /**
     * Whether this is a 'to animation' (i.e., one specified by a "to"
     * but not a "from" value).
     */
    protected boolean toAnimation;

    /**
     * Creates a new SimpleAnimation.
     */
    public SimpleAnimation(TimedElement timedElement,
                           AnimatableElement animatableElement,
                           int calcMode,
                           float[] keyTimes,
                           float[] keySplines,
                           boolean additive,
                           boolean cumulative,
                           AnimatableValue[] values,
                           AnimatableValue from,
                           AnimatableValue to,
                           AnimatableValue by) {
        super(timedElement, animatableElement);

        toAnimation = false;
        if (values == null) {
            if (from != null) {
                values = new AnimatableValue[2];
                values[0] = from;
                if (to != null) {
                    values[1] = to;
                } else if (by != null) {
                    values[1] = to.interpolate(null, null, 0f, by); 
                } else {
                    // XXX
                    throw new RuntimeException("None of 'values', 'to' or 'by' were specified");
                }
            } else {
                if (to != null) {
                    values = new AnimatableValue[1];
                    values[0] = to;
                    toAnimation = true;
                    cumulative = false;
                } else if (by != null) {
                    additive = true;
                    values = new AnimatableValue[2];
                    values[0] = by.getZeroValue();
                    values[1] = by;
                } else {
                    // XXX
                    throw new RuntimeException("None of 'values', 'to' or 'by' were specified");
                }
            }
        }

        if (keyTimes != null) {
            boolean invalidKeyTimes = false;
            if ((calcMode == CALC_MODE_LINEAR || calcMode == CALC_MODE_SPLINE)
                    && (keyTimes.length < 2
                        || keyTimes[0] != 0
                        || keyTimes[keyTimes.length - 1] != 1)
                    || calcMode == CALC_MODE_DISCRETE
                        && (keyTimes.length == 0 || keyTimes[0] != 0)) {
                invalidKeyTimes = true;
            }
            if (!invalidKeyTimes) {
                for (int i = 1; i < keyTimes.length; i++) {
                    if (keyTimes[i] < 0 || keyTimes[1] > 1
                            || keyTimes[i] < keyTimes[i - 1]) {
                        invalidKeyTimes = true;
                        break;
                    }
                }
            }
            if (invalidKeyTimes) {
                // XXX
                throw new RuntimeException("Invalid 'keyTimes' specified");
            }
        } else {
            if (calcMode == CALC_MODE_LINEAR || calcMode == CALC_MODE_SPLINE) {
                int count = values.length == 1 ? 2 : values.length;
                keyTimes = new float[count];
                for (int i = 0; i < count; i++) {
                    keyTimes[i] = i / (count - 1);
                }
            } else if (calcMode == CALC_MODE_DISCRETE) {
                int count = values.length;
                keyTimes = new float[count];
                for (int i = 0; i < count; i++) {
                    keyTimes[i] = i / count;
                }
            }
        }

        if (calcMode == CALC_MODE_SPLINE
                && (keySplines == null
                    || keySplines.length != (keyTimes.length - 1) * 4)) {
            // XXX
            throw new RuntimeException("Invalid 'keySplines' specified");
        }

        this.calcMode = calcMode;
        this.keyTimes = keyTimes;
        this.keySplines = keySplines;
        this.additive = additive;
        this.cumulative = cumulative;
        this.values = values;
        this.from = from;
        this.to = to;
        this.by = by;
    }

    /**
     * Returns whether this animation will replace values on animations
     * lower in the sandwich.
     */
    protected boolean willReplace() {
        return !additive;
    }

    protected void sampledAt(float simpleTime, float simpleDur,
                             int repeatIteration) {
        float unitTime;
        if (simpleDur == TimedElement.INDEFINITE) {
            unitTime = 0;
        } else {
            unitTime = simpleTime / simpleDur;
        }

        AnimatableValue value, accumulation, nextValue;
        float interpolation = 0;
        if (calcMode != CALC_MODE_PACED) {
            int keyTimeIndex = 0;
            while (keyTimeIndex < keyTimes.length - 1
                    && unitTime >= keyTimes[keyTimeIndex + 1]) {
                keyTimeIndex++;
            }
            value = values[keyTimeIndex];
            if (calcMode == CALC_MODE_LINEAR
                    || calcMode == CALC_MODE_SPLINE) {
                nextValue = values[keyTimeIndex + 1];
                if (calcMode == CALC_MODE_SPLINE) {
                    if (unitTime != 0) {
                        Cubic c = new Cubic(0, 0,
                                            keySplines[keyTimeIndex * 4],
                                            keySplines[keyTimeIndex * 4 + 1],
                                            keySplines[keyTimeIndex * 4 + 2],
                                            keySplines[keyTimeIndex * 4 + 3],
                                            1, 1);
                        float tolerance = 0.01f;
                        float min = 0;
                        float max = 1;
                        Point2D.Double p;
                        for (;;) {
                            float t = (min + max) / 2;
                            p = c.eval(t);
                            double x = p.getX();
                            if (Math.abs(x - unitTime) < tolerance) {
                                break;
                            }
                            if (x < t) {
                                max = t;
                            } else {
                                min = t;
                            }
                        }
                        unitTime = (float) p.getY();
                    }
                }
                interpolation = (unitTime - keyTimes[keyTimeIndex])
                    / (keyTimes[keyTimeIndex + 1] - keyTimes[keyTimeIndex]);
            } else {
                nextValue = null;
            }
            if (cumulative) {
                accumulation = values[values.length - 1];
            } else {
                accumulation = null;
            }
        } else {
            // XXX paced
            value = null;
            nextValue = null;
            accumulation = null;
        }

        this.value = value.interpolate(this.value, nextValue, interpolation,
                                       accumulation);
        markDirty();
    }
}
