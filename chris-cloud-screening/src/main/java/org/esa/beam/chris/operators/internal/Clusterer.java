/* Copyright (C) 2002-2008 by Brockmann Consult
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
import java.util.Random;

/**
 * Expectation maximization (EM) cluster algorithm.
 * <p/>
 * todo - observer notifications
 * todo - make algorithm use tiles
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
public class Clusterer {

    private final int pointCount;
    private final int dimensionCount;
    private final double[][] points;
    private final int clusterCount;

    private final double[] p;
    private final double[][] h;
    private final double[][] means;
    private final double[][][] covariances;
    private final Distribution[] distributions;

    /**
     * Finds a collection of clusters for a given set of data points.
     *
     * @param points         the data points.
     * @param clusterCount   the number of clusters.
     * @param iterationCount the number of EM iterations to be made.
     *
     * @return the cluster decomposition.
     */
    public static ClusterSet findClusters(double[][] points, int clusterCount, int iterationCount) {
        return new Clusterer(points, clusterCount).findClusters(iterationCount);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param points       the data points.
     * @param clusterCount the number of clusters.
     */
    public Clusterer(double[][] points, int clusterCount) {
        this(points.length, points[0].length, points, clusterCount, 5489);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param pointCount     the number of data points.
     * @param dimensionCount the number of dimension in point space.
     * @param points         the data points.
     * @param clusterCount   the number of clusters.
     * @param seed           the seed used for random initialization.
     */
    private Clusterer(int pointCount, int dimensionCount, double[][] points, int clusterCount, int seed) {
        // todo: check arguments

        this.pointCount = pointCount;
        this.dimensionCount = dimensionCount;
        this.points = points;
        this.clusterCount = clusterCount;

        p = new double[clusterCount];
        h = new double[clusterCount][pointCount];
        means = new double[clusterCount][dimensionCount];
        covariances = new double[clusterCount][dimensionCount][dimensionCount];
        distributions = new Distribution[clusterCount];

        initialize(new Random(seed));
    }

    /**
     * Finds a collection of clusters.
     *
     * @param iterationCount the number of EM iterations to be made.
     *
     * @return the cluster decomposition.
     */
    private ClusterSet findClusters(int iterationCount) {
        while (iterationCount > 0) {
            iterate();
            iterationCount--;
            // todo - notify observer
        }

        return getClusters();
    }

    /**
     * Carries out a single EM iteration.
     */
    public void iterate() {
        stepE();
        stepM();
    }

    /**
     * Returns the clusters found.
     * todo - make private when observer notifications implemented
     *
     * @return the clusters found.
     */
    public ClusterSet getClusters() {
        return getClusters(new PriorProbabilityClusterComparator());
    }

    public ClusterSet getClusters(Comparator<Cluster> clusterComparator) {
        final Cluster[] clusters = new Cluster[clusterCount];
        for (int k = 0; k < clusterCount; ++k) {
            clusters[k] = new Cluster(distributions[k], p[k]);
        }
        Arrays.sort(clusters, clusterComparator);

        return new ClusterSet(clusters);
    }

    /**
     * Randomly initializes the clusters.
     *
     * @param random the random number generator used for initialization.
     */
    public void initialize(Random random) {
        calculateMeans(random);

        for (int k = 0; k < clusterCount; ++k) {
            calculateCovariances(means[k], covariances[k]);
            p[k] = 1.0;
            distributions[k] = new MultinormalDistribution(means[k], covariances[k]);
        }
    }

    private void calculateMeans(Random random) {
        for (int k = 0; k < clusterCount; ++k) {
            boolean accepted = true;
            do {
                System.arraycopy(points[random.nextInt(pointCount)], 0, means[k], 0, dimensionCount);
                for (int i = 0; i < k; ++i) {
                    accepted = !Arrays.equals(means[k], means[i]);
                    if (!accepted) {
                        break;
                    }
                }
            } while (!accepted);
        }
    }

    private void calculateCovariances(double[] mean, double[][] covariances) {
        for (int i = 0; i < pointCount; ++i) {
            for (int k = 0; k < dimensionCount; ++k) {
                for (int l = k; l < dimensionCount; ++l) {
                    covariances[k][l] += (points[i][k] - mean[k]) * (points[i][l] - mean[l]);
                }
            }
        }
        for (int k = 0; k < dimensionCount; ++k) {
            for (int l = k; l < dimensionCount; ++l) {
                covariances[l][k] = covariances[k][l];
            }
        }
    }

    /**
     * Performs an E-step.
     */
    private void stepE() {
        for (int i = 0; i < pointCount; ++i) {
            double sum = 0.0;
            for (int k = 0; k < clusterCount; ++k) {
                h[k][i] = p[k] * distributions[k].probabilityDensity(points[i]);
                sum += h[k][i];
            }
            if (sum > 0.0) {
                for (int k = 0; k < h.length; ++k) {
                    final double t = h[k][i] / sum;
                    h[k][i] = t;
                }
            } else { // numerical underflow - recompute posterior cluster probabilities
                final double[] sums = new double[clusterCount];
                for (int k = 0; k < clusterCount; ++k) {
                    h[k][i] = distributions[k].logProbabilityDensity(points[i]);
                }
                for (int k = 0; k < clusterCount; ++k) {
                    for (int l = 0; l < clusterCount; ++l) {
                        if (l != k) {
                            sums[k] += (p[l] / p[k]) * exp(h[l][i] - h[k][i]);
                        }
                    }
                }
                for (int k = 0; k < clusterCount; ++k) {
                    final double t = 1.0 / (1.0 + sums[k]);
                    h[k][i] = t;
                }
            }
        }
    }

    /**
     * Performs an M-step.
     */
    private void stepM() {
        for (int k = 0; k < clusterCount; ++k) {
            p[k] = calculateMoments(h[k], means[k], covariances[k]);
            distributions[k] = new MultinormalDistribution(means[k], covariances[k]);
        }
    }

    /**
     * Calculates the statistical moments.
     *
     * @param h           the posterior probabilities associated with the data points.
     * @param mean        the mean of the data points.
     * @param covariances the covariances of the data points.
     *
     * @return the mean posterior probability.
     */
    private double calculateMoments(double[] h, double[] mean, double[][] covariances) {
        for (int k = 0; k < dimensionCount; ++k) {
            for (int l = k; l < dimensionCount; ++l) {
                covariances[k][l] = 0.0;
            }
            mean[k] = 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < pointCount; ++i) {
            for (int k = 0; k < dimensionCount; ++k) {
                mean[k] += h[i] * points[i][k];
            }
            sum += h[i];
        }
        for (int k = 0; k < dimensionCount; ++k) {
            mean[k] /= sum;
        }
        for (int i = 0; i < pointCount; ++i) {
            for (int k = 0; k < dimensionCount; ++k) {
                for (int l = k; l < dimensionCount; ++l) {
                    covariances[k][l] += h[i] * (points[i][k] - mean[k]) * (points[i][l] - mean[l]);
                }
            }
        }
        for (int k = 0; k < dimensionCount; ++k) {
            for (int l = k; l < dimensionCount; ++l) {
                covariances[k][l] /= sum;
                covariances[l][k] = covariances[k][l];
            }
        }

        return sum / pointCount;
    }

    /**
     * Cluster comparator.
     * <p/>
     * Compares two clusters according to their prior probability.
     */
    private static class PriorProbabilityClusterComparator implements Comparator<Cluster> {

        public int compare(Cluster c1, Cluster c2) {
            return Double.compare(c2.getPriorProbability(), c1.getPriorProbability());
        }
    }
}
