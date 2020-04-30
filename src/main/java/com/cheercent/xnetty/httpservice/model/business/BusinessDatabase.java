package com.cheercent.xnetty.httpservice.model.business;

import com.cheercent.xnetty.httpservice.base.XModel;


public abstract class BusinessDatabase extends XModel {

	public static final String dataSourceName = "business";
	
	@Override
	protected String getDataSourceName() {
		return dataSourceName;
	}
	
}
