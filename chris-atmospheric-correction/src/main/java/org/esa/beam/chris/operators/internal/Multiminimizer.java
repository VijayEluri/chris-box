package org.esa.beam.chris.operators.internal;

/**
 * todo - add API doc
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 * @since BEAM 4.2
 */
public interface Multiminimizer {

    double findMinimum(MultivariateFunction f, double[] x);
}
