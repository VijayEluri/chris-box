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
package org.esa.beam.chris.ui;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.swing.BindingContext;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.ParametersPane;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.application.SelectionChangeEvent;
import org.esa.beam.framework.ui.application.SelectionChangeListener;

import javax.swing.*;
import java.awt.*;

/**
 * todo - add API doc
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 * @since BEAM 4.5
 */
class ScreeningForm extends JPanel {

    private final SourceProductSelector sourceProductSelector;
    private final JCheckBox wvCheckBox;
    private final JCheckBox o2CheckBox;

    ScreeningForm(AppContext appContext, final ScreeningFormModel formModel) {
        sourceProductSelector = new SourceProductSelector(appContext);

        // configure product selector
        sourceProductSelector.setProductFilter(new CloudScreeningProductFilter());
        final JComboBox comboBox = sourceProductSelector.getProductNameComboBox();
        comboBox.setPrototypeDisplayValue("[1] CHRIS_HH_HHHHHH_HHHH_HH_NR");
        final ValueContainer vc1 = formModel.getProductValueContainer();
        final BindingContext bc1 = new BindingContext(vc1);
        bc1.bind("sourceProduct", comboBox);

        // create parameters panel
        final ValueContainer vc2 = formModel.getParameterValueContainer();
        final BindingContext bc2 = new BindingContext(vc2);
        final JPanel panel = new ParametersPane(bc2).createPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Processing Parameters"));

        wvCheckBox = (JCheckBox) bc2.getBinding("useWv").getComponents()[0];
        o2CheckBox = (JCheckBox) bc2.getBinding("useO2").getComponents()[0];

        // disable check boxes when corresponding features are not available
        sourceProductSelector.addSelectionChangeListener(new SelectionChangeListener() {
            @Override
            public void selectionChanged(SelectionChangeEvent event) {
                final Product selectedProduct = (Product) event.getSelection().getFirstElement();
                final boolean available = checkFeatureAvailability(selectedProduct);

                wvCheckBox.setEnabled(available);
                o2CheckBox.setEnabled(available);
                if (!available) {
                    wvCheckBox.setSelected(false);
                    o2CheckBox.setSelected(false);
                }
            }

            private boolean checkFeatureAvailability(Product product) {
                return product != null && product.getProductType().matches("CHRIS_M[15].*_NR");
            }
        });

        setLayout(new BorderLayout(4, 4));
        add(sourceProductSelector.createDefaultPanel(), BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
    }

    void prepareHide() {
        sourceProductSelector.releaseProducts();
    }

    void prepareShow() {
        sourceProductSelector.initProducts();
        if (sourceProductSelector.getProductCount() > 0) {
            sourceProductSelector.setSelectedIndex(0);
        }
    }

    SourceProductSelector getSourceProductSelector() {
        return sourceProductSelector;
    }

    Product getSourceProduct() {
        return sourceProductSelector.getSelectedProduct();
    }

    void setSourceProduct(Product product) {
        sourceProductSelector.setSelectedProduct(product);
    }

    boolean isWvCheckBoxEnabled() {
        return wvCheckBox.isEnabled();
    }

    boolean isWvCheckBoxSelected() {
        return wvCheckBox.isSelected();
    }

    boolean isO2CheckBoxEnabled() {
        return o2CheckBox.isEnabled();
    }

    boolean isO2CheckBoxSelected() {
        return o2CheckBox.isSelected();
    }
}