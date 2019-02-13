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
package de.mpicbg.knime.scripting.python.prefs;

import de.mpicbg.knime.scripting.core.prefs.TemplateTableEditor;
import de.mpicbg.knime.scripting.core.utils.ScriptingUtils;
import de.mpicbg.knime.scripting.python.PythonScriptingBundleActivator;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;


/**
 * @author Tom Haux (MPI-CBG)
 */
public class PythonPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    /**
     * Creates a new preference page.
     */
    public PythonPreferencePage() {
        super(GRID);

        setPreferenceStore(PythonScriptingBundleActivator.getDefault().getPreferenceStore());
    }


    @Override
    protected void createFieldEditors() {
        Composite parent = getFieldEditorParent();
        
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        String bundlePath = ScriptingUtils.getBundlePath(bundle).toOSString();
        
        Path cacheFolder = Paths.get(bundlePath, ScriptingUtils.LOCAL_CACHE_FOLDER);
        Path indexFile = Paths.get(bundlePath, ScriptingUtils.LOCAL_CACHE_FOLDER, ScriptingUtils.LOCAL_CACHE_INDEX);

        addField(new StringFieldEditor(PythonPreferenceInitializer.PYTHON_HOST, "The host where the Python server is running", parent));
        addField(new IntegerFieldEditor(PythonPreferenceInitializer.PYTHON_PORT, "The port on which Python server is listening", parent));

        addField(new BooleanFieldEditor(PythonPreferenceInitializer.PYTHON_LOCAL, "Run python scripts on local system (ignores host/port settings)", parent));
        addField(new StringFieldEditor(PythonPreferenceInitializer.PYTHON_EXECUTABLE, "The path to the local python executable", parent));

        addField(new TemplateTableEditor(PythonPreferenceInitializer.PYTHON_TEMPLATE_RESOURCES, "Snippet template resources", cacheFolder, indexFile, parent));
        addField(new TemplateTableEditor(PythonPreferenceInitializer.PYTHON_PLOT_TEMPLATE_RESOURCES, "Plot template resource", cacheFolder, indexFile, parent));
    }


    public void init(final IWorkbench workbench) {
        // nothing to do
    }
}