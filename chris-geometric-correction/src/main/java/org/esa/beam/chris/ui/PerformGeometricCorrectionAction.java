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

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.chris.operators.PerformGeometricCorrectionOp;
import org.esa.beam.chris.operators.TimeConverter;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;

import javax.swing.JOptionPane;
import java.awt.Window;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Action for invoking the geometric correction.
 *
 * @author Ralf Quast
 * @since CHRIS-Box 1.5
 */
public class PerformGeometricCorrectionAction extends AbstractVisatAction {

    private static final String TITLE = "CHRIS/Proba Geometric Correction";
    private static final String KEY_FETCH_LATEST_TIME_TABLES = "beam.chris.fetchLatestTimeTables";
    private static final String QUESTION_FETCH_LATEST_TIME_TABLES =
            "Your UT1 and leap second time tables are older than 7 days. Fetching\n" +
            "the latest time tables from the web can take a few minutes.\n" +
            "\n" +
            "Do you want to fetch the latest time tables now?";

    private final AtomicReference<ModelessDialog> dialog;

    public PerformGeometricCorrectionAction() {
        dialog = new AtomicReference<ModelessDialog>();
    }

    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        final TimeConverter converter;

        try {
            converter = TimeConverter.getInstance();
        } catch (IOException e) {
            getAppContext().handleError("The geometric correction cannot be carried out because an error occurred.", e);
            return;
        }
        if (converter.isOutdated()) {
            final int answer = showQuestionDialog(QUESTION_FETCH_LATEST_TIME_TABLES, KEY_FETCH_LATEST_TIME_TABLES);
            if (answer == JOptionPane.YES_OPTION) {
                final Window applicationWindow = getAppContext().getApplicationWindow();
                final TimeConverterUpdater updater = new TimeConverterUpdater(applicationWindow, TITLE, converter);
                updater.execute();
                // dialog is created and shown when updater is done
            } else {
                dialog.compareAndSet(null, createDialog());
                dialog.get().show();
            }
        } else {
            dialog.compareAndSet(null, createDialog());
            dialog.get().show();
        }
    }

    @Override
    public void updateState() {
        final Product selectedProduct = getAppContext().getSelectedProduct();
        setEnabled(selectedProduct == null || new GeometricCorrectionProductFilter().accept(selectedProduct));
    }

    private ModelessDialog createDialog() {
        final String operatorAlias = OperatorSpi.getOperatorAlias(PerformGeometricCorrectionOp.class);
        return new GeometricCorrectionDialog(operatorAlias, getAppContext(), TITLE, getHelpId());
    }

    private class TimeConverterUpdater extends ProgressMonitorSwingWorker<Object, Object> {

        private final TimeConverter converter;

        public TimeConverterUpdater(Window applicationWindow, String title, TimeConverter converter) {
            super(applicationWindow, title);
            this.converter = converter;
        }

        @Override
        protected Object doInBackground(ProgressMonitor pm) throws Exception {
            converter.updateTimeTables(pm);
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException e) {
                getAppContext().handleError("An error occurred while updating the UT1 and leap second time tables.", e);
                return;
            } catch (ExecutionException e) {
                getAppContext().handleError("An error occurred while updating the UT1 and leap second time tables.",
                                            e.getCause());
                return;
            }
            showInfoDialog("The UT1 and leap second time tables have been updated successfully.");
            dialog.compareAndSet(null, createDialog());
            dialog.get().show();
        }
    }

    private static void showInfoDialog(String message) {
        VisatApp.getApp().showInfoDialog(TITLE, message, null);
    }

    private static int showQuestionDialog(String message, String preferencesKey) {
        return VisatApp.getApp().showQuestionDialog(TITLE, message, preferencesKey);
    }
}
