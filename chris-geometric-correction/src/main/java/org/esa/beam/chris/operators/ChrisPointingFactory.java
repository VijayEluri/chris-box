package org.esa.beam.chris.operators;

import org.esa.beam.framework.datamodel.Pointing;
import org.esa.beam.framework.datamodel.PointingFactory;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.TiePointGridPointing;

public final class ChrisPointingFactory implements PointingFactory {

    @Override
    public String[] getSupportedProductTypes() {
        return new String[]{
                "CHRIS_M0_NR_AC_GC",
                "CHRIS_M1_NR_AC_GC",
                "CHRIS_M2_NR_AC_GC",
                "CHRIS_M3_NR_AC_GC",
                "CHRIS_M4_NR_AC_GC",
                "CHRIS_M5_NR_AC_GC"
        };
    }

    @Override
    public Pointing createPointing(final RasterDataNode raster) {
        final Product product = raster.getProduct();
        return new TiePointGridPointing(product.getGeoCoding(),
                                        null,
                                        null,
                                        product.getTiePointGrid("vza"),
                                        product.getTiePointGrid("vaa"),
                                        null);
    }
}
