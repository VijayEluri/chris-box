/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.chris.ui;


import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Action for invoking the CHIRS/Proba cloud screening dialog.
 *
 * @author Marco Peters
 * @author Ralf Quast
 * @author Marco Zuehlke
 * @version $Revision$ $Date$
 * @since BEAM 4.2
 */
public class CloudScreeningAction extends AbstractVisatAction {
    static final String HELP_ID = "chrisCloudScreeningTools";

    private final AtomicReference<ModelessDialog> dialog;

    public CloudScreeningAction() {
        dialog = new AtomicReference<ModelessDialog>();
    }

    @Override
    public void actionPerformed(CommandEvent event) {
        dialog.compareAndSet(null, createDialog(getAppContext()));
        dialog.get().show();
    }

    @Override
    public void updateState() {
        final Product selectedProduct = getAppContext().getSelectedProduct();
        setEnabled(selectedProduct == null || new CloudScreeningProductFilter().accept(selectedProduct));
    }

    private static ModelessDialog createDialog(AppContext appContext) {
        final ModelessDialog dialog = new ScreeningDialog(appContext);
        dialog.getJDialog().setName("chrisCloudScreeningDialog");

        return dialog;
    }

}
