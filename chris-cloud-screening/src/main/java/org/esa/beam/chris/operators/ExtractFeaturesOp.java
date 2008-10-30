package org.esa.beam.chris.operators;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.chris.operators.InclusiveBandFilter;
import org.esa.beam.chris.operators.InclusiveMultiBandFilter;
import org.esa.beam.chris.operators.StrictlyInclusiveBandFilter;
import org.esa.beam.chris.util.BandFilter;
import org.esa.beam.chris.util.OpUtils;
import org.esa.beam.dataio.chris.ChrisConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.IOException;
import static java.lang.Math.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Operator for extracting features from TOA reflectances needed for
 * cloud screening.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
@OperatorMetadata(alias = "chris.ExtractFeatures",
                  version = "1.0",
                  authors = "Ralf Quast",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "Extracts features from TOA reflectances needed for cloud screening.")
public class ExtractFeaturesOp extends Operator {

    private static final double INVERSE_SCALING_FACTOR = 10000.0;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    private transient Band br;
    private transient Band wh;
    private transient Band visBr;
    private transient Band visWh;
    private transient Band nirBr;
    private transient Band nirWh;
    private transient Band o2;
    private transient Band wv;

    private transient Band[] surfaceBands;
    private transient Band[] visBands;
    private transient Band[] nirBands;

    private transient boolean canComputeAtmosphericFeatures;
    private transient double trO2;
    private transient double trWv;
    private transient BandInterpolator interpolatorO2;
    private transient BandInterpolator interpolatorWv;
    private transient double mu;

    @Override
    public void initialize() throws OperatorException {
        final Band[] reflectanceBands = getReflectanceBands(sourceProduct.getBands());
        categorizeBands(reflectanceBands);

        canComputeAtmosphericFeatures = sourceProduct.getProductType().matches("CHRIS_M[15]_REFL");

        if (canComputeAtmosphericFeatures) {
            interpolatorO2 = new BandInterpolator(reflectanceBands,
                                                  new double[]{760.625, 755.0, 770.0, 738.0, 755.0, 770.0, 788.0});
            interpolatorWv = new BandInterpolator(reflectanceBands,
                                                  new double[]{944.376, 895.0, 960.0, 865.0, 890.0, 985.0, 1100.0});
            final double[][] transmittanceTable = readTransmittanceTable();
            trO2 = getAverageValue(transmittanceTable, interpolatorO2.getInnerWavelength(),
                                   interpolatorO2.getInnerBandwidth());
            trWv = getAverageValue(transmittanceTable, interpolatorWv.getInnerWavelength(),
                                   interpolatorWv.getInnerBandwidth());

            final double sza = OpUtils.getAnnotationDouble(sourceProduct, ChrisConstants.ATTR_NAME_SOLAR_ZENITH_ANGLE);
            final double vza = OpUtils.getAnnotation(sourceProduct, ChrisConstants.ATTR_NAME_OBSERVATION_ZENITH_ANGLE,
                                                     0.0);
            mu = 1.0 / (1.0 / cos(toRadians(sza)) + 1.0 / cos(toRadians(vza)));
        }

        final String type = sourceProduct.getProductType() + "_FEAT";
        targetProduct = new Product("CHRIS_FEATURES", type,
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        br = targetProduct.addBand("brightness", ProductData.TYPE_INT16);
        br.setDescription("Brightness for visual and NIR bands");
        br.setUnit("dl");
        br.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        visBr = targetProduct.addBand("brightness_vis", ProductData.TYPE_INT16);
        visBr.setDescription("Brightness for visual bands");
        visBr.setUnit("dl");
        visBr.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        nirBr = targetProduct.addBand("brightness_nir", ProductData.TYPE_INT16);
        nirBr.setDescription("Brightness for NIR bands");
        nirBr.setUnit("dl");
        nirBr.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        wh = targetProduct.addBand("whiteness", ProductData.TYPE_INT16);
        wh.setDescription("Whiteness for visual and NIR bands");
        wh.setUnit("dl");
        wh.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        visWh = targetProduct.addBand("whiteness_vis", ProductData.TYPE_INT16);
        visWh.setDescription("Whiteness for visual bands");
        visWh.setUnit("dl");
        visWh.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        nirWh = targetProduct.addBand("whiteness_nir", ProductData.TYPE_INT16);
        nirWh.setDescription("Whiteness for NIR bands");
        nirWh.setUnit("dl");
        nirWh.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

        if (canComputeAtmosphericFeatures) {
            o2 = targetProduct.addBand("o2", ProductData.TYPE_INT16);
            o2.setDescription("Atmospheric oxygen absorption");
            o2.setUnit("dl");
            o2.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);

            wv = targetProduct.addBand("wv", ProductData.TYPE_INT16);
            wv.setDescription("Atmospheric water vapour absorption");
            wv.setUnit("dl");
            wv.setScalingFactor(1.0 / INVERSE_SCALING_FACTOR);
        }

        ProductUtils.copyMetadata(sourceProduct.getMetadataRoot(), targetProduct.getMetadataRoot());
        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());
    }

    private static Band[] getReflectanceBands(Band[] bands) {
        final List<Band> reflectanceBandList = new ArrayList<Band>(bands.length);
        for (final Band band : bands) {
            if (band.getName().startsWith("toa_refl")) {
                reflectanceBandList.add(band);
            }
        }
        final Band[] reflectanceBands = reflectanceBandList.toArray(new Band[reflectanceBandList.size()]);
        Arrays.sort(reflectanceBands, new BandComparator());

        return reflectanceBands;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        if (canComputeAtmosphericFeatures) {
            pm.beginTask("computing bands...", 8);
        } else {
            pm.beginTask("computing bands...", 6);
        }
        try {
            computeSurfaceFeatures(br, wh, targetTileMap, targetRectangle, surfaceBands,
                                   SubProgressMonitor.create(pm, 2));
            computeSurfaceFeatures(visBr, visWh, targetTileMap, targetRectangle, visBands,
                                   SubProgressMonitor.create(pm, 2));
            computeSurfaceFeatures(nirBr, nirWh, targetTileMap, targetRectangle, nirBands,
                                   SubProgressMonitor.create(pm, 2));
            if (canComputeAtmosphericFeatures) {
                computeAtmosphericFeature(o2, targetTileMap, targetRectangle, interpolatorO2, trO2,
                                          SubProgressMonitor.create(pm, 1));
                computeAtmosphericFeature(wv, targetTileMap, targetRectangle, interpolatorWv, trWv,
                                          SubProgressMonitor.create(pm, 1));
            }
        } finally {
            pm.done();
        }
    }

    @Override
    public void dispose() {
        br = null;
        wh = null;
        visBr = null;
        visWh = null;
        nirBr = null;
        nirWh = null;
        o2 = null;
        wv = null;

        surfaceBands = null;
        visBands = null;
        nirBands = null;

        interpolatorO2 = null;
        interpolatorWv = null;
    }

    private void categorizeBands(Band[] bands) {
        final List<Band> surfaceBandList = new ArrayList<Band>(bands.length);
        final List<Band> visBandList = new ArrayList<Band>(bands.length);
        final List<Band> nirBandList = new ArrayList<Band>(bands.length);

        final BandFilter visBandFilter = new InclusiveBandFilter(400.0, 700.0);
        final BandFilter absBandFilter = new InclusiveMultiBandFilter(new double[][]{
                {400.0, 440.0},
                {590.0, 600.0},
                {630.0, 636.0},
                {648.0, 658.0},
                {686.0, 709.0},
                {792.0, 799.0},
                {756.0, 775.0},
                {808.0, 840.0},
                {885.0, 985.0},
                {985.0, 1010.0}});

        for (final Band band : bands) {
            if (absBandFilter.accept(band)) {
                continue;
            }
            surfaceBandList.add(band);
            if (visBandFilter.accept(band)) {
                visBandList.add(band);
            } else {
                nirBandList.add(band);
            }
        }

        if (surfaceBandList.isEmpty()) {
            throw new OperatorException("no absorption-free bands found");
        }
        if (visBandList.isEmpty()) {
            throw new OperatorException("no absorption-free visual bands found");
        }
        if (nirBandList.isEmpty()) {
            throw new OperatorException("no absorption-free NIR bands found");
        }

        surfaceBands = surfaceBandList.toArray(new Band[surfaceBandList.size()]);
        visBands = visBandList.toArray(new Band[visBandList.size()]);
        nirBands = nirBandList.toArray(new Band[nirBandList.size()]);
    }

    void computeSurfaceFeatures(Band bBand, Band wBand, Map<Band, Tile> targetTileMap, Rectangle targetRectangle,
                                Band[] sourceBands, ProgressMonitor pm) {
        pm.beginTask("computing surface features...", targetRectangle.height);
        try {
            final double[] wavelengths = getSpectralWavelengths(sourceBands);
            final Tile[] sourceTiles = getSourceTiles(sourceBands, targetRectangle, pm);

            final Tile bTile = targetTileMap.get(bBand);
            final Tile wTile = targetTileMap.get(wBand);

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; ++y) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x) {
                    checkForCancelation(pm);

                    final double[] reflectances = getSamples(x, y, sourceTiles);
                    final double b = brightness(wavelengths, reflectances);
                    final double w = whiteness(wavelengths, reflectances, b);

                    bTile.setSample(x, y, b);
                    wTile.setSample(x, y, w);
                }

                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private Tile[] getSourceTiles(Band[] bands, Rectangle targetRectangle, ProgressMonitor pm) {
        final Tile[] sourceTiles = new Tile[bands.length];

        for (int i = 0; i < bands.length; ++i) {
            sourceTiles[i] = getSourceTile(bands[i], targetRectangle, pm);
        }
        return sourceTiles;
    }

    private static double[] getSpectralWavelengths(Band[] bands) {
        final double[] wavelengths = new double[bands.length];

        for (int i = 0; i < bands.length; ++i) {
            wavelengths[i] = bands[i].getSpectralWavelength();
        }
        return wavelengths;
    }

    private void computeAtmosphericFeature(Band targetBand, Map<Band, Tile> targetTileMap, Rectangle targetRectangle,
                                           BandInterpolator bandInterpolator, double transmittance,
                                           ProgressMonitor pm) {
        pm.beginTask("computing optical path...", targetRectangle.height);
        try {
            final Band sourceBand = bandInterpolator.getInnerBand();
            final Band[] infBands = bandInterpolator.getInfBands();
            final Band[] supBands = bandInterpolator.getSupBands();

            final Tile sourceTile = getSourceTile(sourceBand, targetRectangle, pm);
            final Tile targetTile = targetTileMap.get(targetBand);

            final Tile[] infTiles = getSourceTiles(infBands, targetRectangle, pm);
            final Tile[] supTiles = getSourceTiles(supBands, targetRectangle, pm);

            final double c = mu / log(transmittance);

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; ++y) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x) {
                    checkForCancelation(pm);

                    final double a = getMean(x, y, infTiles);
                    final double b = getMean(x, y, supTiles);
                    final double f = c * log(sourceTile.getSampleDouble(x, y) / bandInterpolator.getValue(a, b));

                    targetTile.setSample(x, y, f);
                }

                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private static double[] getSamples(int x, int y, Tile[] tiles) {
        final double[] samples = new double[tiles.length];

        for (int i = 0; i < samples.length; i++) {
            samples[i] = tiles[i].getSampleDouble(x, y);
        }

        return samples;
    }

    private static double getMean(int x, int y, Tile[] tiles) {
        double sum = 0.0;

        for (final Tile tile : tiles) {
            sum += tile.getSampleDouble(x, y);
        }

        return sum / tiles.length;
    }

    // todo - move or make an averager class
    private static double getAverageValue(double[][] table, double wavelength, double width) {
        final double[] x = table[0];
        final double[] y = table[1];

        double ws = 0.0;
        double ys = 0.0;

        for (int i = 0; i < table[0].length; ++i) {
            if (x[i] > wavelength + width) {
                break;
            }
            if (x[i] > wavelength - width) {
                final double w = 1.0 / pow(1.0 + abs(2.0 * (x[i] - wavelength) / width), 4.0);

                ys += y[i] * w;
                ws += w;
            }
        }

        return ys / ws;
    }

    private static double brightness(double[] wavelengths, double[] reflectances) {
        double sum = 0.0;

        for (int i = 1; i < reflectances.length; ++i) {
            sum += 0.5 * (reflectances[i] + reflectances[i - 1]) * (wavelengths[i] - wavelengths[i - 1]);
        }

        return sum / (wavelengths[wavelengths.length - 1] - wavelengths[0]);
    }

    private static double whiteness(double[] wavelengths, double[] reflectances, double brightness) {
        double sum = 0.0;

        for (int i = 1; i < reflectances.length; ++i) {
            sum += 0.5 * (abs(reflectances[i] - brightness) + abs(
                    reflectances[i - 1] - brightness)) * (wavelengths[i] - wavelengths[i - 1]);
        }

        return sum / (wavelengths[wavelengths.length - 1] - wavelengths[0]);
    }

    static double[][] readTransmittanceTable() throws OperatorException {
        final ImageInputStream iis = OpUtils.getResourceAsImageInputStream(ExtractFeaturesOp.class,
                                                                           "nir-transmittance.img");

        try {
            final int length = iis.readInt();
            final double[] abscissas = new double[length];
            final double[] ordinates = new double[length];

            iis.readFully(abscissas, 0, length);
            iis.readFully(ordinates, 0, length);

            return new double[][]{abscissas, ordinates};
        } catch (Exception e) {
            throw new OperatorException("could not read NIR transmittance table", e);
        } finally {
            try {
                iis.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ExtractFeaturesOp.class);
        }
    }


    private static class BandInterpolator {

        private final Band innerBand;
        private final Band[] infBands;
        private final Band[] supBands;

        private final double interpolationWeight;

        public BandInterpolator(Band[] bands, double[] wavelengths) {
            innerBand = findProximateBand(bands, wavelengths[0], new StrictlyInclusiveBandFilter(wavelengths[1],
                                                                                                 wavelengths[2]));

            infBands = findBands(bands, new StrictlyInclusiveBandFilter(wavelengths[3], wavelengths[4]));
            supBands = findBands(bands, new StrictlyInclusiveBandFilter(wavelengths[5], wavelengths[6]));

            if (innerBand == null) {
                throw new OperatorException(MessageFormat.format(
                        "no absorption band found for wavelength {0} nm", wavelengths[0]));
            }
            if (infBands.length == 0 && supBands.length == 0) {
                throw new OperatorException(MessageFormat.format(
                        "no interpolation bands found for wavelength {0} nm", wavelengths[0]));
            }
            final double a = meanWavelength(infBands);
            final double b = meanWavelength(supBands);

            interpolationWeight = (innerBand.getSpectralWavelength() - a) / (b - a);
        }

        public final Band getInnerBand() {
            return innerBand;
        }

        public double getInnerWavelength() {
            return innerBand.getSpectralWavelength();
        }

        public double getInnerBandwidth() {
            return innerBand.getSpectralBandwidth();
        }

        public final Band[] getInfBands() {
            return infBands;
        }

        public final Band[] getSupBands() {
            return supBands;
        }

        public double getValue(double a, double b) {
            if (infBands.length == 0) {
                return b;
            }
            if (supBands.length == 0) {
                return a;
            }

            return (1.0 - interpolationWeight) * a + interpolationWeight * b;
        }

        private static Band[] findBands(Band[] bands, BandFilter bandFilter) {
            final List<Band> bandList = new ArrayList<Band>();

            for (final Band band : bands) {
                if (bandFilter.accept(band)) {
                    bandList.add(band);
                }
            }

            return bandList.toArray(new Band[bandList.size()]);
        }

        private static Band findProximateBand(Band[] bands, double wavelength, BandFilter bandFilter) {
            Band proximateBand = null;

            for (final Band band : bands) {
                if (bandFilter.accept(band)) {
                    if (proximateBand == null || dist(proximateBand, wavelength) > dist(band, wavelength)) {
                        proximateBand = band;
                    }
                }
            }

            return proximateBand;
        }

        private static double dist(Band band, double wavelength) {
            return abs(band.getSpectralWavelength() - wavelength);
        }

        private static double meanWavelength(Band[] bands) {
            double sum = 0.0;

            for (final Band band : bands) {
                sum += band.getSpectralWavelength();
            }

            return sum / bands.length;
        }
    }
}
