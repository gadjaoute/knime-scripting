/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2010
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, version 2, as 
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * ------------------------------------------------------------------------
 *
 * History
 *   19.09.2007 (thiel): created
 */
package de.mpicbg.knime.scripting.matlab.prefs;

import de.mpicbg.knime.scripting.matlab.AbstractMatlabScriptingNodeModel;
import de.mpicbg.knime.scripting.matlab.MatlabScriptingBundleActivator;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;


public class MatlabPreferenceInitializer extends AbstractPreferenceInitializer {

	/** Property name of the MATLAB type of the variable that will hold the KNIME table in the MATLAB workspace */
	public static final String MATLAB_TYPE = "matlab.type";
	
	/** Property name of the MATLAB session number (how many instances of the MATLAB application will run simultaneously */
	public static final String MATLAB_SESSIONS = "matlab.sessions";
    
    /** Property name of the data transfer method between KNIME and MATLAB */
    public static final String MATLAB_TRANSFER_METHOD = "matlab.transfer.method";

    /** Property name of the snippet template resource url */
    public static final String MATLAB_TEMPLATE_RESOURCES = "template.resources";
    
    /** Property name of the plot template resource url */
    public static final String MATLAB_PLOT_TEMPLATE_RESOURCES = "plot.template.resources";


    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = MatlabScriptingBundleActivator.getDefault().getPreferenceStore();
        
        store.setDefault(MATLAB_SESSIONS, 1);

        store.setDefault(MATLAB_TEMPLATE_RESOURCES, "(\"https://raw.githubusercontent.com/knime-mpicbg/scripting-templates/master/knime-scripting-templates/Matlab/script-templates.txt\",true)");
        store.setDefault(MATLAB_PLOT_TEMPLATE_RESOURCES, "(\"https://raw.githubusercontent.com/knime-mpicbg/scripting-templates/master/knime-scripting-templates/Matlab/figure-templates.txt\",true)");
        
        store.setDefault(MATLAB_TYPE, AbstractMatlabScriptingNodeModel.DEFAULT_TYPE);
        store.setDefault(MATLAB_TRANSFER_METHOD, "file");
    }
}   
