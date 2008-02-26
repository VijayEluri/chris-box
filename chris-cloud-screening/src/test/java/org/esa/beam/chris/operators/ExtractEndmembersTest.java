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
package org.esa.beam.chris.operators;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.unmixing.Endmember;
import org.esa.beam.util.jai.RasterDataNodeOpImage;

import java.io.IOException;

/**
 * Tests for class {@link ExtractEndmembersOp}.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
public class ExtractEndmembersTest extends TestCase {

    public void testCalculateEndmembers() throws IOException {
        final double[][] reflectances = {
                new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6},
                new double[]{0.6, 0.1, 0.2, 0.3, 0.4, 0.5},
                new double[]{0.5, 0.6, 0.1, 0.2, 0.3, 0.4},
                new double[]{0.3, 0.4, 0.5, 0.6, 0.1, 0.2},
        };
        final Product reflectanceProduct = createReflectanceProduct(reflectances);
        final String[] featureNames = {"brightness_vis", "whiteness_vis"};
        final double[][] features = {
                new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6},
                new double[]{0.2, 0.3, 0.1, 0.5, 0.6, 0.1},
        };
        final Product featureProduct = createFeatureProduct(featureNames, features);
        final double[][] probabilities = {
                new double[]{0.1, 0.6, 0.1, 0.6, 0.0, 0.3},
                new double[]{0.2, 0.1, 0.6, 0.3, 0.3, 0.0},
                new double[]{0.1, 0.1, 0.2, 0.1, 0.1, 0.6},
                new double[]{0.0, 0.2, 0.0, 0.0, 0.6, 0.0},
                new double[]{0.6, 0.0, 0.1, 0.0, 0.0, 0.1},
        };
        final Product clusterProduct = createClusterProduct(probabilities);

        final ExtractEndmembersOp op = new ExtractEndmembersOp(reflectanceProduct, featureProduct, clusterProduct,
                new int[]{1, 4}, new int[]{0, 2}, new String[]{"Erni", "Bert"});

        final Endmember[] endmembers = op.calculateEndmembers(ProgressMonitor.NULL);

        assertNotNull(endmembers);
        assertEquals(3, endmembers.length);
        assertEquals("Cloud", endmembers[0].getName());
        assertEquals("Erni", endmembers[1].getName());
        assertEquals("Bert", endmembers[2].getName());
        assertEquals(4, endmembers[0].getSize());

        for (int i = 0; i < 4; ++i) {
            assertEquals("i = " + i, reflectances[i][2], endmembers[0].getRadiation(i), 0.0);
        }
        for (int i = 0; i < 4; ++i) {
            final double mean = (reflectances[i][1] + reflectances[i][3]) / 2.0;
            assertEquals("i = " + i, mean, endmembers[1].getRadiation(i), 0.0);
        }
        for (int i = 0; i < 4; ++i) {
            assertEquals("i = " + i, reflectances[i][5], endmembers[2].getRadiation(i), 0.0);
        }
    }

    private static Product createReflectanceProduct(double[][] values) {
        final Product reflectanceProduct = new Product("R", "R", 3, 2);

        for (int i = 0; i < values.length; ++i) {
            addSyntheticBand(reflectanceProduct, "reflectance_" + i, values[i]);
        }

        return reflectanceProduct;
    }

    private static Product createFeatureProduct(String[] featureNames, double[][] features) {
        final Product featureProduct = new Product("F", "F", 3, 2);

        for (int i = 0; i < featureNames.length; ++i) {
            addSyntheticBand(featureProduct, featureNames[i], features[i]);
        }

        return featureProduct;
    }

    private static Product createClusterProduct(double[][] probabilities) {
        final int[] memberships = calculateMemberships(probabilities);

        final Product clusterProduct = new Product("C", "C", 3, 2);
        for (int i = 0; i < probabilities.length; ++i) {
            addSyntheticBand(clusterProduct, "probability_" + i, probabilities[i]);
        }
        addSyntheticBand(clusterProduct, "membership_mask", memberships);

        return clusterProduct;
    }

    private static int[] calculateMemberships(double[][] probabilities) {
        final int[] memberships = new int[probabilities[0].length];

        for (int j = 0; j < memberships.length; ++j) {
            int index = 0;
            for (int i = 1; i < probabilities.length; ++i) {
                if (probabilities[i][j] > probabilities[index][j]) {
                    index = i;
                }
            }
            memberships[j] = index;
        }

        return memberships;
    }

    private static Band addSyntheticBand(Product product, String name, int[] values) {
        final Band band = product.addBand(name, ProductData.TYPE_INT32);

        band.setSynthetic(true);
        band.setRasterData(ProductData.createInstance(values));
        band.setImage(new RasterDataNodeOpImage(band));

        return band;
    }

    private static Band addSyntheticBand(Product product, String name, double[] values) {
        final Band band = product.addBand(name, ProductData.TYPE_FLOAT64);

        band.setSynthetic(true);
        band.setRasterData(ProductData.createInstance(values));
        band.setImage(new RasterDataNodeOpImage(band));

        return band;
    }
}
