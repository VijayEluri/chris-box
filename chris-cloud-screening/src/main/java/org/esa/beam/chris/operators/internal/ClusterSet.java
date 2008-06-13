/* 
 * Copyright (C) 2002-2008 by Brockmann Consult
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.chris.operators.internal;

import static java.lang.Math.exp;
import java.util.Arrays;
import java.util.Comparator;

/**
 * todo - add API doc
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 * @since BEAM 4.2
 */
public class ClusterSet {

    private final Cluster[] clusters;

    public ClusterSet(Cluster[] clusters) {
        this.clusters = clusters;
    }

    public double[] getPosteriorProbabilities(double[] point) {
        double sum = 0.0;
        final double[] h = new double[clusters.length];
        for (int k = 0; k < clusters.length; ++k) {
            h[k] = clusters[k].priorProbability * clusters[k].getPprobabilityDensity(point);
            sum += h[k];
        }
        if (sum > 0.0) {
            for (int k = 0; k < h.length; ++k) {
                final double t = h[k] / sum;
                h[k] = t;
            }
        } else { // numerical underflow - recompute posterior cluster probabilities
            final double[] sums = new double[clusters.length];
            for (int k = 0; k < clusters.length; ++k) {
                h[k] = clusters[k].getLogProbabilityDensity(point);
            }
            for (int k = 0; k < clusters.length; ++k) {
                for (int l = 0; l < clusters.length; ++l) {
                    if (l != k) {
                        sums[k] += (clusters[l].getPriorProbability() / clusters[k].getPriorProbability()) * exp(h[l] - h[k]);
                    }
                }
            }
            for (int k = 0; k < clusters.length; ++k) {
                final double t = 1.0 / (1.0 + sums[k]);
                h[k] = t;
            }
        }
        return h;
    }

    public final int getClusterCount() {
        return clusters.length;
    }

    public double[] getMean(int i) {
        return clusters[i].getMean();
    }

    private static class PriorProbabilityClusterComparator implements Comparator<Cluster> {
        @Override
        public int compare(Cluster c1, Cluster c2) {
            return Double.compare(c2.priorProbability, c1.priorProbability);
        }
    }
}
