package de.mpicbg.knime.scripting.matlab.ctrl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabOperations;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;

import de.mpicbg.knime.knutils.Attribute;
import de.mpicbg.knime.knutils.AttributeUtils;
import de.mpicbg.knime.knutils.InputTableAttribute;
import de.mpicbg.knime.scripting.core.exceptions.KnimeScriptingException;
import de.mpicbg.knime.scripting.matlab.AbstractMatlabScriptingNodeModel;


/**
 * This Class is a parser for KNIME tables.
 * There are two methods. One use the serial connection to push 
 * the values directly to the Matlab workspace. The second methods parses 
 * the KNIME table in a LinkedHashMap (since LinkedHashMap's are supported
 * by Matlab we can dump the Java object to a file and read it from Matlab)
 * 
 * @author Holger Brandl, Felix Meyenhofer
 */
public class MatlabTable {
	
	/** KNIME table object */
	private BufferedDataTable table;
	
	/** KNIME table in the for of a linked hash-map */
	private LinkedHashMap<String, Object> hash;
	
	/** Temp file for the data */
	private File hashTempFile;
		
	/**
	 * Constructor 
	 * 
	 * @param table KNIME table
	 */
	public MatlabTable(BufferedDataTable table) {
		this.table = table;
	}
	
	/**
	 * Constructor
	 * 
	 * @param hash KNIME data in the form of a linked hash-map.
	 */
	public MatlabTable(LinkedHashMap<String, Object> hash) {
		this.hash = hash;
	}
	
	public MatlabTable(File hashTempFile) {
		this.hashTempFile = hashTempFile;
	}
	
	/**
	 * Getter for the temp-file containing the KNIME table data
	 * 
	 * @return {@link File} pointing to the hash-map object dump
	 */
	public File getTempFile() {
		return this.hashTempFile;
	}
	
	public String getHashMapTempPath() {
		return this.hashTempFile.getAbsolutePath();
	}
	
	/**
	 * Getter for the KNIME table
	 * 
	 * @return KNIME table
	 */
	public BufferedDataTable getBufferedDataTable() {
		return this.table;
	}
	
	/**
	 * Getter for the transformed KNIME table
	 * 
	 * @return Transformed KNIME table
	 */
	public LinkedHashMap<String, Object> getLinkedHashMap() {
		return this.hash;
	}
	
	/**
     * Conversion of a KNIME table (see {@link BufferedDataTable}) to a {@link LinkedHashMap}
     * 
     * @param table Input table form KNIME
     * @return {@link LinkedHashMap}
	 * @throws MatlabCellTypeException 
     */
    public void knimeTable2LinkedHashMap()
    	throws KnimeScriptingException  {
    	
        DataTableSpec tableSpec = this.table.getDataTableSpec();
        long tableSize = this.table.size();
        if(tableSize > Integer.MAX_VALUE)
        	throw new KnimeScriptingException("Cannot process tables with more than " + Integer.MAX_VALUE + " rows (Integer.MAX_VALUE)");
        
        // Initialize the hash.
        LinkedHashMap<String, Object> hashTable = new LinkedHashMap<String, Object>();
        for (int j = 0; j < tableSpec.getNumColumns(); j++) {
            DataColumnSpec columnSpec = tableSpec.getColumnSpec(j);
            if (columnSpec.getType().isCompatible(StringValue.class)) {
                hashTable.put(columnSpec.getName(), new String[(int)tableSize]);
            } else {
                hashTable.put(columnSpec.getName(), new Double[(int)tableSize]);
            }
        }
        
        // Parse the data from the KNIME table into the hash.
        int i = 0;
        for (DataRow row : this.table) {

            for (int j = 0; j < row.getNumCells(); j++) {

                DataColumnSpec columnSpec = tableSpec.getColumnSpec(j);

                if (columnSpec.getType().equals(StringCell.TYPE)) {
                    String[] str = (String[]) hashTable.get(columnSpec.getName());
                    Attribute<StringValue> readoutAttribute = new InputTableAttribute<StringValue>(columnSpec.getName(), this.table);
                    str[i] = readoutAttribute.getNominalAttribute(row);
                    hashTable.put(columnSpec.getName(), str);
                } else if (columnSpec.getType().isCompatible(IntValue.class) || 
                		(columnSpec.getType().isCompatible(DoubleValue.class))) {
                    Double[] num = (Double[]) hashTable.get(columnSpec.getName());
                    Attribute<DoubleValue> readoutAttribute = new InputTableAttribute<DoubleValue>(columnSpec.getName(), this.table);
                    num[i] = readoutAttribute.getDoubleAttribute(row);
                    hashTable.put(columnSpec.getName(), num);
                } else {
                	throw new RuntimeException("Unsupported cell type.");
                }
            }
            i++;
        }
        
        this.hash = hashTable;
    }
	
	/**
	 * Transform the {@link LinkedHashMap} back to a {@link BufferedDataTable} 
	 * (MATLAB java object back to KNIME data table)
	 *  
	 * @param exec Execution context of the KNIME node
	 */
    public void linkedHashMap2KnimeTable(ExecutionContext exec) {
    	
    	// Determine the column attributes
    	List<Attribute> colAttributes = createColumnAttributeList(this.hash);
    	
        // Get the number of samples (rows)
        int numSamples = 0;
        Object colData = this.hash.get(colAttributes.get(0).getName());
        if (colData != null) {
	        if (colData.getClass().isArray()) {        	
	        	numSamples = Array.getLength(colData);
	        } else {
	        	numSamples = 1;
	        }
        }
//        else if (firstAttribute.getType().isCompatible(DoubleValue.class)) {
//            numSamples = ((double[]) colData).length;
//        } else if ((firstAttribute.getType().isCompatible(IntValue.class))) {
//            numSamples = ((int[]) colData).length;
//        } else if (firstAttribute.getType().isCompatible(StringValue.class)) {
//            numSamples = ((String[]) colData).length;
//        } colData.getClass().

        // create the cell matrix
        int columnLength = numSamples;
        DataCell[][] cells = new DataCell[numSamples][colAttributes.size()];

        for (int colIndex = 0; colIndex < colAttributes.size(); colIndex++) {
            Attribute columnAttribute = colAttributes.get(colIndex);
            Object curColumn = this.hash.get(columnAttribute.getName());
            
            if (curColumn == null) {
            	// fall through
            } else if (columnAttribute.getType().isCompatible(DoubleValue.class)) {
            	if (curColumn.getClass().isArray()) {
            		double[] doubleColumn = (double[]) curColumn;
                    for (int rowIndex = 0; rowIndex < doubleColumn.length; rowIndex++) {
                        cells[rowIndex][colIndex] = new DoubleCell(doubleColumn[rowIndex]);
                    }
            	} else {
            		cells[0][colIndex] = new DoubleCell((double)curColumn); 
            	}
            } else if (columnAttribute.getType().isCompatible(IntValue.class)) {
            	if (curColumn.getClass().isArray()) {
	                int[] intColumn = (int[]) curColumn;
	                for (int rowIndex = 0; rowIndex < intColumn.length; rowIndex++) {
	                    cells[rowIndex][colIndex] = new IntCell(intColumn[rowIndex]);
	                }
            	} else {
            		cells[0][colIndex] = new IntCell((int)curColumn);
            	}
            } else if (columnAttribute.getType().isCompatible(StringValue.class)) {
            	if (curColumn.getClass().isArray()) {
	                String[] stringColumn = (String[]) curColumn;
	                columnLength = stringColumn.length;
	                for (int rowIndex = 0; rowIndex < numSamples; rowIndex++) {
	                    if (stringColumn[rowIndex] == null) {
	                        cells[rowIndex][colIndex] = DataType.getMissingCell();
	                    } else {
	                        if (stringColumn[rowIndex].isEmpty()) {
	                            cells[rowIndex][colIndex] = DataType.getMissingCell();
	                        } else {
	                            cells[rowIndex][colIndex] = new StringCell(stringColumn[rowIndex]);
	                        }
	                    }
	                }
            	} else {
            		String value = (String) curColumn;            		
            		if (value == null || value.isEmpty()) {
            			cells[0][colIndex] = DataType.getMissingCell();
            		} else {
            			cells[0][colIndex] = new StringCell(value);
            		}
            	}
            }
            if (columnLength != numSamples) {
                throw new RuntimeException("The Columns do not have the same lenght!");
            }
        }

        // convert cell matrix into KNIME table
        DataTableSpec outputSpec = AttributeUtils.compileTableSpecs(colAttributes);
        BufferedDataContainer container = exec.createDataContainer(outputSpec);

        for (int i = 0; i < numSamples; i++) {
            RowKey key = new RowKey("" + i);
            DataRow row = new DefaultRow(key, cells[i]);
            container.addRowToTable(row);
        }

        container.close();
        this.table = container.getTable();
    }
    
	/**
	 * Make an object dump of the KNIME data in the MATLAB understandable
	 * java object.
	 * 
	 * @throws IOException
	 * @throws MatlabCellTypeException 
	 */
    public void writeHashMapToTempFolder() 
    		throws IOException, KnimeScriptingException {
    	if (this.hash == null)
    		knimeTable2LinkedHashMap();
    	
        File file = File.createTempFile(AbstractMatlabScriptingNodeModel.TABLE_TEMP_FILE_PREFIX, AbstractMatlabScriptingNodeModel.TABLE_TEMP_FILE_SUFFIX);
        file.deleteOnExit();
        FileOutputStream fileStream = new FileOutputStream(file);
        ObjectOutputStream serializedObject = new ObjectOutputStream(fileStream);
        serializedObject.writeObject(this.hash);
        serializedObject.close();
        this.hashTempFile = file;
        this.hash = null;
    }
    
    /**
     * Read the MATLAB understandable java object dump.
     * 
     * @param exec Node execution context
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void readHashMapFromTempFolder(ExecutionContext exec) throws IOException, ClassNotFoundException  {
    	InputStream fileStream = new FileInputStream(this.hashTempFile);
    	ObjectInputStream objectStream = new ObjectInputStream(fileStream);
        this.hash = (LinkedHashMap<String, Object>) objectStream.readObject();;
        linkedHashMap2KnimeTable(exec);
        objectStream.close();
        this.hashTempFile.delete();
    }
    
    /**
     * Get an input stream from the hash map object.
     * This method allows to bypass the local temp-file of the data table
     * when operating with a remote MATLAB host. So the data is streamed 
     * to the server and only there it is saved to a file.
     * The {@link MatlabFileTransfer} class has the complementary functionality
     * to achieve this.
     * 
     * @return
     * @throws IOException
     */
    public InputStream getHashMapObjectStream() throws IOException {
    	 ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	 ObjectOutputStream oos = new ObjectOutputStream(baos);
    	 oos.writeObject(this.hash);
    	 oos.flush();
    	 oos.close();
    	 return new ByteArrayInputStream(baos.toByteArray());
    }
    
    /**
     * Create the column specs from the {@link LinkedHashMap} 
     * (MATLAB understandable java object)
     * 
     * @param hash Table data
     * @return List of KNIME table attributes (specs)
     */
    public static List<Attribute> createColumnAttributeList(LinkedHashMap<String, Object> hash) {
        // Initialize the columnSpec table
        List<Attribute> colSpec = new ArrayList<Attribute>();
        Attribute attribute;
        for (Object attributeKey : hash.keySet()) {
            Object colData = hash.get(attributeKey);
            if ((colData instanceof int[]) || (colData instanceof Integer)) {
                attribute = new Attribute(attributeKey.toString(), IntCell.TYPE);
            } else if ((colData instanceof double[]) || (colData instanceof Double)) {
                attribute = new Attribute(attributeKey.toString(), DoubleCell.TYPE);
            } else if ((colData instanceof List) || (colData instanceof String[]) || (colData instanceof String)) {
                attribute = new Attribute(attributeKey.toString(), StringCell.TYPE);
            } else if ((colData instanceof boolean[]) || (colData instanceof Boolean)) {
                throw new RuntimeException("logical columns are not yet supported");
            } else {
                // If the proper way does not work, try it dirty.
                try {
                    colData = (double[]) colData;
                    attribute = new Attribute(attributeKey.toString(), DoubleCell.TYPE);
                } catch (Exception e) {
                    try {
                        colData = (int[]) colData;
                        attribute = new Attribute(attributeKey.toString(), IntCell.TYPE);
                    } catch (Exception ee) {
                        try {
                            colData = (String[]) colData;
                            attribute = new Attribute(attributeKey.toString(), StringCell.TYPE);
                        } catch (Exception eee) {
                            System.err.println("Unsupported column type: " + colData.getClass().getName());
                            continue;
                        }
                    }
                }
            }
            colSpec.add(attribute);
        }
        return colSpec;
    }

    /**
     * Use the serial connection to Matlab to push directly into the workspace
     * 
     * @param proxy
     * @param matlabType
     * @throws MatlabInvocationException
     */
    public void pushTable2MatlabWorkspace(MatlabOperations proxy, String matlabType) throws MatlabInvocationException {
    	// Get the column names
    	List<String> colNames = new ArrayList<String>();
    	List<DataType> colTypes = new ArrayList<DataType>();
        for (DataColumnSpec colSpec : this.table.getDataTableSpec()) {
            colNames.add(colSpec.getName());
            colTypes.add(colSpec.getType());
        }
        
        List<String> varNames = MatlabCode.getVariableNamesFromColumnNames(matlabType, colNames);
        
        // Initialize the input variable
        String instanciationCmd = MatlabCode.getInputVariableInstanciationCommand(matlabType, varNames, colTypes);
        proxy.eval(instanciationCmd);
        
        // Push the data line by line
        String appendRowCmd;
        for (DataRow dataRow : this.table) {
        	appendRowCmd = MatlabCode.getAppendRowCommand(matlabType, dataRow, varNames);
        	proxy.eval(appendRowCmd);
        }
        
        // Finish up
        String additionalInfo = MatlabCode.getInputColumnAdditionalInformationCommand(matlabType, varNames, colNames);
        proxy.eval(additionalInfo);
    }

    /**
     * Pull data directly form the Matlab workspace
     * 
     * @param exec
     * @param proxy
     * @param matlabType
     * @return
     * @throws MatlabInvocationException
     */
	public BufferedDataTable pullTableFromMatlabWorkspace(ExecutionContext exec, MatlabOperations proxy, String matlabType) throws MatlabInvocationException {
		// Fetch the column names and types
		String[] varNames = (String[]) proxy.getVariable(MatlabCode.getOutputColumnNamesCommand(matlabType));
//		String[] varDescr = (String[]) proxy.getVariable(MatlabCode.getOutputVariableDescriptionsCommand(matlabType));
		String[] varTypes = (String[]) proxy.getVariable(MatlabCode.getOutputColumnTypesCommand(matlabType));
		
		// Get the number of rows
		double numRows = ((double[]) proxy.getVariable(MatlabCode.getOutputTableNumberOfRowsCommand(matlabType)))[0];
		int numCols = varNames.length;
		
		// Compile the table specifications
		DataColumnSpec[] colSpecs = new DataColumnSpec[numCols];
		for (int i = 0; i < numCols; i++) {
			if (varTypes[i].equals("double"))
				colSpecs[i] = new DataColumnSpecCreator(varNames[i], DoubleCell.TYPE).createSpec();
			else if (varTypes[i].equals("char") || varTypes[i].equals("cell"))
				colSpecs[i] = new DataColumnSpecCreator(varNames[i], StringCell.TYPE).createSpec();
			else
				throw new RuntimeException("Unsupported MATLAB type '" + varTypes[i] + " (#." + i + ").");
		}
		
		// Pull the table data
		DataTableSpec outputSpec = new DataTableSpec(colSpecs);
		BufferedDataContainer container = exec.createDataContainer(outputSpec);
		DataCell[] cells = new DataCell[numCols];
		for (int i = 0; i < numRows; i++) {
			// Retrieve a row from the MATLAB workspace
			Object[] data = (Object[]) proxy.getVariable(MatlabCode.getRetrieveOutputRowCommand(matlabType, i+1, varNames));

			// Prepare the cells
			for (int j = 0; j < numCols; j++) {
				if (colSpecs[j].getType().equals(DoubleCell.TYPE))
					cells[j] = new DoubleCell(((double[])data[j])[0]);
				else
					cells[j] = new StringCell(((String[]) data[j])[0]);
			}
			
			// Append the row to the table
			RowKey key = new RowKey("" + i);
			DataRow row = new DefaultRow(key, cells);
			container.addRowToTable(row);
		}

		container.close();
		return container.getTable();
	}    
    
    /**
     * Cleanup the files and object to liberate disk and memory space
     * TODO: make sure we got everything.
     */
	public void cleanup() {
		if (hashTempFile != null)
			hashTempFile.delete();
		hash = null;
	}	
}
