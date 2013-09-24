/*
 * Copyright 2009-2013 PrimeTek.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.primefaces.component.export;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.el.MethodExpression;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.UIColumn;
import javax.faces.component.UIComponent;
import javax.faces.component.UIData;
import javax.faces.component.ValueHolder;
import javax.faces.component.html.HtmlCommandLink;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;

import org.primefaces.component.datatable.DataTable;

public abstract class Exporter {
	    
	protected enum ColumnType{
		HEADER("header"),
		FOOTER("footer");
        
        private final String facet;
        
        ColumnType(String facet) {
            this.facet = facet;
        }

        public String facet() {
            return facet;
        }
        
        @Override
        public String toString() {
            return facet;
        }
	};

    public abstract void export(FacesContext facesContext, DataTable table,
			String outputFileName, boolean pageOnly, boolean selectionOnly,
			String encodingType, MethodExpression preProcessor,
			MethodExpression postProcessor) throws IOException;

	
	protected List<UIColumn> getColumnsToExport(UIData table) {
        List<UIColumn> columns = new ArrayList<UIColumn>();
        int columnIndex = -1;

        for(UIComponent child : table.getChildren()) {
            if(child instanceof UIColumn) {
                UIColumn column = (UIColumn) child;
                columnIndex++;

                columns.add(column);
            }
        }

        return columns;
    }

    protected boolean hasColumnFooter(List<UIColumn> columns) {
        for(UIColumn column : columns) {
            if(column.getFooter() != null)
                return true;
        }

        return false;
    }

    protected String exportValue(FacesContext context, UIComponent component) {

        if(component instanceof HtmlCommandLink) {  //support for PrimeFaces and standard HtmlCommandLink
            HtmlCommandLink link = (HtmlCommandLink) component;
            Object value = link.getValue();

            if(value != null) {
                return String.valueOf(value);
            } 
            else {
                //export first value holder
                for(UIComponent child : link.getChildren()) {
                    if(child instanceof ValueHolder) {
                        return exportValue(context, child);
                    }
                }

                return "";
            }
        }
        else if(component instanceof ValueHolder) {
 
			if(component instanceof EditableValueHolder) {
				Object submittedValue = ((EditableValueHolder) component).getSubmittedValue();
				if (submittedValue != null) {
					return submittedValue.toString();
				}
			}

			ValueHolder valueHolder = (ValueHolder) component;
			Object value = valueHolder.getValue();
			if(value == null)
				return "";
			
            Converter converter = valueHolder.getConverter();
            if(converter == null) {
                Class valueType = value.getClass();
                converter = context.getApplication().createConverter(valueType);
            }
            
            if(converter != null) {
                return converter.getAsString(context, component, value);
            }
			
			//No converter found just return the value as string
			return value.toString();
		} 
        else {
			//This would get the plain texts on UIInstructions when using Facelets
			String value = component.toString();

			if(value != null)
				return value.trim();
			else
				return "";
		}
    }
    
    protected void exportPageOnly(FacesContext context, DataTable table, Object document) {        
        int first = table.getFirst();
    	int rowsToExport = first + table.getRows();
        
        for(int rowIndex = first; rowIndex < rowsToExport; rowIndex++) {                
            exportRow(table, document, rowIndex);
        }
    }
    
    protected void exportAll(FacesContext context, DataTable table, Object document) {
        int first = table.getFirst();
    	int rowCount = table.getRowCount();
        int rows = table.getRows();
        boolean lazy = table.isLazy();
        
        if(lazy) {
            for(int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                if(rowIndex % rows == 0) {
                    table.setFirst(rowIndex);
                    table.setRowIndex(-1);
                    table.loadLazyData();
                }

                exportRow(table, document, rowIndex);
            }
     
            //restore
            table.setFirst(first);
            table.setRowIndex(-1);
            table.loadLazyData();
        } 
        else {
            for(int rowIndex = 0; rowIndex < rowCount; rowIndex++) {                
                exportRow(table, document, rowIndex);
            }
            
            //restore
            table.setFirst(first);
        }
    }

    protected void exportRow(DataTable table, Object document, int rowIndex) {
        table.setRowIndex(rowIndex);
        if(!table.isRowAvailable()) {
            return;
        }
       
        preRowExport(table, document);
        exportCells(table, document);
        postRowExport(table, document);
    }
    
    protected void exportSelectionOnly(FacesContext context, DataTable table, Object document) {        
        Object selection = table.getSelection();
        String var = table.getVar();
        
        if(selection != null) {
            Map<String,Object> requestMap = context.getExternalContext().getRequestMap();

            if(selection.getClass().isArray()) {
                int size = Array.getLength(selection);
                
                for(int i = 0; i < size; i++) {
                    requestMap.put(var, Array.get(selection, i));
                    exportCells(table, document);
                }
            }
            else if(List.class.isAssignableFrom(selection.getClass())) {
                List<?> list = (List) selection;
                
                for(int i = 0; i < list.size(); i++) {
                    requestMap.put(var, list.get(i));
                    exportCells(table, document);
                }
            }
            else {
                requestMap.put(var, selection);
                exportCells(table, document);
            }
        }
    }
    
    protected void preRowExport(DataTable table, Object document) {}
    
    protected void postRowExport(DataTable table, Object document) {}
    
    protected abstract void exportCells(DataTable table, Object document);
}
