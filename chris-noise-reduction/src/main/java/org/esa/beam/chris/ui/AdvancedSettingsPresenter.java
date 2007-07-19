package org.esa.beam.chris.ui;

import com.bc.ceres.binding.Factory;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.ConverterRegistry;
import com.bc.ceres.binding.converters.EnumConverter;
import org.esa.beam.chris.operators.DestripingFactorsOp;
import org.esa.beam.chris.operators.DropoutCorrectionOp;
import org.esa.beam.framework.gpf.annotations.ParameterDefinitionFactory;
import org.esa.beam.dataio.chris.internal.DropoutCorrection;

import java.util.HashMap;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @author Ralf Quast
 * @version $Revision:$ $Date:$
 */
class AdvancedSettingsPresenter {

    static {
        final ConverterRegistry converterRegistry = ConverterRegistry.getInstance();
        final Class<DropoutCorrection.Neigborhood> type = DropoutCorrection.Neigborhood.class;
        if (converterRegistry.getConverter(type) == null) {
            converterRegistry.setConverter(type, new EnumConverter<DropoutCorrection.Neigborhood>(type));
        }
    }

    private HashMap<String, Object> destripingParameterMap;
    private HashMap<String, Object> dropoutCorrectionParameterMap;
    private ValueContainer destripingValueContainer;
    private ValueContainer dropoutCorrectionValueContainer;

    public AdvancedSettingsPresenter() {
        destripingParameterMap = new HashMap<String, Object>();
        dropoutCorrectionParameterMap = new HashMap<String, Object>();

        initValueContainers();
    }

    private AdvancedSettingsPresenter(HashMap<String, Object> destripingParameterMap,
                                      HashMap<String, Object> dropoutCorrectionParameterMap) {
        this.destripingParameterMap = new HashMap<String, Object>(destripingParameterMap);
        this.dropoutCorrectionParameterMap = new HashMap<String, Object>(dropoutCorrectionParameterMap);

        initValueContainers();
    }

    private void initValueContainers() {
        final Factory factory = new Factory(new ParameterDefinitionFactory());
        destripingValueContainer =
                factory.createMapBackedValueContainer(DestripingFactorsOp.class, destripingParameterMap);
        dropoutCorrectionValueContainer =
                factory.createMapBackedValueContainer(DropoutCorrectionOp.class, dropoutCorrectionParameterMap);
    }

    public ValueContainer getDestripingValueContainer() {
        return destripingValueContainer;
    }

    public HashMap<String, Object> getDestripingParameterMap() {
        return destripingParameterMap;
    }

    public HashMap<String, Object> getDropoutCorrectionParameterMap() {
        return dropoutCorrectionParameterMap;
    }

    public ValueContainer getDropoutCorrectionValueContainer() {
        return dropoutCorrectionValueContainer;
    }

    // todo - replace with clone()
    public AdvancedSettingsPresenter createCopy() {
        return new AdvancedSettingsPresenter(destripingParameterMap, dropoutCorrectionParameterMap);
    }

}
