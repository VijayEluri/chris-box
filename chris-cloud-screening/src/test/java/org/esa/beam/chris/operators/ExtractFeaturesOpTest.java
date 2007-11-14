package org.esa.beam.chris.operators;

import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

/**
 * Tests for class {@link ExtractFeaturesOp}.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
public class ExtractFeaturesOpTest extends TestCase {

    public void testAssertValidity() {
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M1_REFL", 10, 10));
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M2_REFL", 10, 10));
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M3_REFL", 10, 10));
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M3A_REFL", 10, 10));
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M4_REFL", 10, 10));
        ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M5_REFL", 10, 10));

        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M1", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M2", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M3", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M3A", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M4", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
        try {
            ExtractFeaturesOp.assertValidity(new Product("name", "CHRIS_M5", 10, 10));
            fail();
        } catch (OperatorException expected) {
        }
    }

    public void testTransmittanceTableIntegrity() throws IOException {
        assertTransmittanceTableIntegrity();
    }

    private static void assertTransmittanceTableIntegrity() throws IOException {
        final InputStream is = ExtractFeaturesOpTest.class.getResourceAsStream("nir-transmittance.txt");

        final Scanner scanner = new Scanner(is);
        scanner.useLocale(Locale.ENGLISH);

        final int rowCount = 4334;
        final ArrayList<Double> x = new ArrayList<Double>(rowCount);
        final ArrayList<Double> y = new ArrayList<Double>(rowCount);

        try {
            while (scanner.hasNext()) {
                x.add(scanner.nextDouble());
                y.add(scanner.nextDouble());
            }
        } finally {
            scanner.close();
        }

        assertEquals(rowCount, x.size());
        assertEquals(rowCount, y.size());

//        final FileImageOutputStream ios = new FileImageOutputStream(new File("nir-transmittance.img"));
//        ios.writeInt(rowCount);
//        for (final Double value : x) {
//            ios.writeDouble(value);
//        }
//        for (final Double value : y) {
//            ios.writeDouble(value);
//        }
//        ios.close();

        final double[][] table = ExtractFeaturesOp.readTransmittanceTable();

        assertEquals(rowCount, table[0].length);
        assertEquals(rowCount, table[1].length);

        for (int i = 0; i < rowCount; ++i) {
            assertEquals(x.get(i), table[0][i]);
            assertEquals(y.get(i), table[1][i]);
        }
    }
}