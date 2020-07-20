function [kIn, columnNames] = hashmaputils(filePath, data, varargin)
%
% HASHMAPUTILS
%
% Depending on the input data this utility reads a the file of a 
% serialized java hasmap (produced by KNIME) and transforms it into a 
% common matlab object. It also allows to convert matlabdata into a 
% LinkedHashMap and to serialize the object.
% The script has to be launched in the directory where it lies and expects
% the data-file to lie in the same directory too.
%
% [data columnNames] = hashmaputils(filePath, data)
%
%       filePath: String indicating the path to a data temp-file (for 
%                        loading or saving data).
%       data: Can be a string indicating the matlab data type of the table
%             variable {dataset (default), map, struct}.
%             Or it can be a matlab object containing the data to be saved
%             as a LinkedHashMap.
%             The action (load or save) is inferred from the object type 
%             of this input.
%
%       Output:
%       kIn: KIME data table
%       columnNames: column names of the KNIME table (useful if struct is
%                    used since this type does not allow all the characters
%                    that can appear in the KNIME table column header.
%

% Author: Felix Meyenhofer
% Date: 04.11.2010
% Institution: Max Planck Institut fo Cell Biology and Genetics


% Handle the input
parser = inputParser();
parser.addRequired('filePath', @(x)exist(x, 'file'));
parser.addRequired('data', @(x)validatedata(x));
parser.addOptional('columnMapping', containers.Map(), @(x)isa(x, 'containers.Map'));
parser.parse(filePath, data, varargin{:});
input = parser.Results();


% Infer the action to take.
if ischar(input.data) % No inputdata -> see if we can load something.
    [kIn, columnNames] = loadhashmap(input.filePath, input.data);
else                  % We have data -> save it.
    savehashmap(input.data, input.filePath, input.columnMapping);
end
   


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%



function out = validatedata(in)
    if ischar(in)
        out = ismember(in, {'table', 'dataset', 'map', 'struct'});
    else
        out = ismember(class(in), {'table', 'dataset', 'containers.Map', 'struct'});
    end
    
    
    
function savehashmap(mTable, filePath, columnMapping)

    % Initialize java object.
    jTable = java.util.LinkedHashMap();

    % Get the column names.
    switch class(mTable)    
        case 'dataset'
            mColNames = get(mTable, 'VarNames');
            kColNames = get(mTable, 'VarDescription');
            command = 'mTable.(mColNames{c})';
            
        case 'containers.Map'
            mColNames = mTable.keys();
            command = 'mTable(mColNames{c})';
            
        case 'struct'
            mColNames = fieldnames(mTable);
            command = 'mTable.(mColNames{c})';
            
        case 'table'
            mColNames = mTable.Properties.VariableNames;
            kColNames = mTable.Properties.VariableDescriptions;
            command = 'mTable.(mColNames{c})';
    end
    
    % Take the user defined output column names, the existing column
    % mapping or the variable names as ouput column names
    if exist('kColNames', 'var') && ~isempty(kColNames)
        cMap = containers.Map(mColNames, kColNames);
    elseif columnMapping.Count == numel(mColNames) && ...
            all(cellfun(@strcmp, mColNames, columnMapping.keys()))
        cMap = columnMapping;
    else
        cMap = containers.Map(mColNames, mColNames);
    end

    % convert the columns.
    for c = 1:numel(mColNames)
        jTable.put(cMap(mColNames{c}), eval(command));
        eval([command '=[];']); % Free the memory
    end

    % Create a file.
    file = java.io.File(filePath);
    file.deleteOnExit();
    % Create the output stream.
    fileStream = java.io.FileOutputStream(file);
    % Serialize the object.
    serializedObject = java.io.ObjectOutputStream(fileStream);
    serializedObject.writeObject(jTable);
    serializedObject.close();
    
    
    
function [kIn, columnMapping] = loadhashmap(filePath, dataType)

    % Load the the object dump of the KNIME table.
    inputStream = java.io.FileInputStream(filePath);
    object = java.io.ObjectInputStream (inputStream);
    hashmap = javaObject('java.util.LinkedHashMap', object.readObject());
    inputStream.close();


    % Initialize matlab object.
    switch dataType
        case 'map'
            kIn = containers.Map();
            
        case 'struct'
            kIn = struct();
            
        case {'dataset', 'table'}
            if license('test', 'statistics_toolbox') == 1
                if strcmp(dataType, 'dataset')
                    kIn = dataset;
                else
                    kIn = table;
                end
            else
                warning('TDS:hashmaputils', ...
                    'The Statistics Toolbox is not available, changed the datatype to "containers.Map".')
                kIn = containers.Map();
                dataType = 'map';
            end
            
        otherwise
            error('KNIME:hashmaputils', ['Unknown option: "' dataTpe '".'])
    end


    % Get the keys of the HashMap and generate unique variable names.
    keys = hashmap.keySet();
    keys = keys.toArray();
    columnNames = cell(numel(keys), 1);
    variableNames = columnNames;
    for n = 1:numel(keys)
        columnNames{n} = char(keys(n));
        variableNames{n} = regexprep(columnNames{n}, '[^0-9a-zA-Z_]','');
    end
    variableNames = genvarname(variableNames);

    
    % Convert data Type.
    for n = 1:numel(columnNames)
        % Get the data and convert it to double or cell array.
        vector = hashmap.get(keys(n));
        hashmap.put(keys(n),[]); % Free the memory
        vector = cell(vector);
        I = cellfun(@isempty, vector);
        try
            vector(I) = {NaN};
            vector = cell2mat(vector);
        catch em
            if any(ismember(em.identifier, {'MATLAB:cell2mat:MixedDataTypes' 'MATLAB:catenate:dimensionMismatch'}))
                vector(I) = {'EMPTY'};
                vector = cell(vector);
            else
                vector = cell(size(I));
                fprintf('\n%s\n%s\n', em.identifier, em.message)
            end
        end

        % Parse the data in the matlab object.
        switch dataType
            case 'map'
                kIn(variableNames{n}) = vector;
            case 'struct'
                kIn.(variableNames{n}) = vector;
            case 'dataset'
                kIn = cat(2, kIn, dataset({vector, variableNames{n}}));
            case 'table'
                kIn.(variableNames{n}) = vector;
        end

    end
    
    % Where possible pack more information into the variable.
    switch dataType
    	case 'dataset'
        	index = 1:length(kIn);
        	index = cellstr(num2str(index(:)));
            kIn = set(kIn, 'VarDescription', columnNames);
            if all(cellfun(@(x) ~isempty(x), index))
            	kIn = set(kIn, 'ObsNames', index);
            end
    	case 'table'
    		index = 1:size(kIn, 1);
        	index = cellstr(num2str(index(:)));
        	kIn.Properties.VariableDescriptions = columnNames;
            if all(cellfun(@(x) ~isempty(x), index))
                kIn.Properties.RowNames = index;
            end
    end
    
    % Keep the mapping between unique variable names and knime column names
    columnMapping = containers.Map(variableNames, columnNames);
