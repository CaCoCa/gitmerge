/*
 *      Copyright (c) 2018-2028, Chill Zhuang All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the dreamlu.net developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: Chill 庄骞 (smallchill@163.com)
 */
package com.etop.property.right.service.impl;

import com.etop.property.right.common.constant.RightRegisterConstants;
import com.etop.property.right.common.constant.utils.EnumUtil;
import com.etop.property.right.common.constant.utils.NumberUtil;
import com.etop.property.right.entity.*;
import com.etop.property.right.model.CompanyInfoAndRegisterInfo;
import com.etop.property.right.model.InfoContentDetail;
import com.etop.property.right.model.InfoContentForm;
import com.etop.property.right.model.InfoTemplateDetail;
import com.etop.property.right.service.*;
import com.etop.property.right.vo.InfoContentVO;
import com.etop.property.right.mapper.InfoContentMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.BeanUtil;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 信息采集表-填写值 服务实现类
 *
 * @author ytct
 * @since 2019-12-16
 */
@Service
public class InfoContentServiceImpl extends ServiceImpl<InfoContentMapper, InfoContent> implements IInfoContentService {

	@Resource
	private InfoTemplateServiceImpl infoTemplateService;

	@Resource
	private ICollectOwnService collectOwnService;
	@Resource
	private ICollectChangeService collectChangeService;
	@Resource
	private ICollectDestroyService collectDestroyService;
	@Resource
	private IRightRegisterService rightRegisterService;

	private final static  String OWN = "own";

	private final static  String CHANGE = "change";


	@Override
	public IPage<InfoContentVO> selectInfoContentPage(IPage<InfoContentVO> page, InfoContentVO infoContent) {
		return page.setRecords(baseMapper.selectInfoContentPage(page, infoContent));
	}


	@Override
	public List<InfoContentDetail> getInfoContentDetail(Long rrId) {
		return baseMapper.getInfoContentDetail(rrId,null);
	}

	@Override
	@Transactional
	public R delInfoContent(Long id) {
		InfoContent content = baseMapper.getInfoContent(id);
		if(null==content){
			return R.fail("要删除的数据不存在,请确保数据的准确性");
		}
		CompanyInfoAndRegisterInfo rightRegister = (CompanyInfoAndRegisterInfo) rightRegisterService.getRightRegisterById(content.getRrId()).getData();

		if(null==rightRegister){
			return R.fail("请确认数据是否已被删除");
		}
		Integer status = rightRegister.getStatus();
		if(RightRegisterConstants.Status.SUBMITTED.getValue().equals(status)){
			return R.fail("正在审核中，暂不能进行编辑操作");
		}else if(RightRegisterConstants.Status.AUDITPASS.getValue().equals(status)){
			return R.fail("已审核通过，不能进行编辑操作");
		}
		Integer tableType = content.getTableType();
		if(null==tableType){
			return R.fail("表格类型缺失");
		}
		Long rrId = content.getRrId();
		if(null==rrId){
			return R.fail("请确保数据的准确性");
		}
		baseMapper.removeInfoContent(id);

		//删除数据检查表格是否还有数据,当没有数据时更新对应的字段:设置有无字段的值为0
		List<InfoContentDetail> contentDetails = baseMapper.getInfoContentDetail(rrId,tableType);
		if(contentDetails.isEmpty()){
			updateFieldValue(rrId,tableType, (Integer) RightRegisterConstants.YesOrNo.NO.getValue());
		}
		return R.status(true);
	}

	@Override
	@Transactional
	public R saveInfoContent(InfoContentForm contentForm) {
		contentForm.setId(null);
		R validTableFieldTypeResult = validTableFieldType(contentForm);
		if(!validTableFieldTypeResult.isSuccess()){
			return validTableFieldTypeResult;
		}
		Long rrId = contentForm.getRrId();
		Integer tableType = contentForm.getTableType();
		//表格增加数据时更新对应的字段:设置有无字段的值为1
		updateFieldValue(rrId,tableType, (Integer) RightRegisterConstants.YesOrNo.YES.getValue());

		if(baseMapper.saveInfoContent(BeanUtil.copy(contentForm,InfoContent.class))<1){
			throw new RuntimeException("保存操作失败");
		}

		return R.data(baseMapper.getInfoContentDetail(rrId,tableType));
	}

	@Override
	public R updateInfoContent(InfoContentForm contentForm) {
		CompanyInfoAndRegisterInfo rightRegister = (CompanyInfoAndRegisterInfo) rightRegisterService.getRightRegisterById(contentForm.getRrId()).getData();

		if(null==rightRegister){
			return R.fail("请确认数据是否已被删除");
		}
		Integer status = rightRegister.getStatus();
		if(RightRegisterConstants.Status.SUBMITTED.getValue().equals(status)){
			return R.fail("正在审核中，暂不能进行编辑操作");
		}else if(RightRegisterConstants.Status.AUDITPASS.getValue().equals(status)){
			return R.fail("已审核通过，不能进行编辑操作");
		}

		R validTableFieldTypeResult = validTableFieldType(contentForm);
		if(!validTableFieldTypeResult.isSuccess()){
			return validTableFieldTypeResult;
		}
		baseMapper.updateInfoContent(BeanUtil.copy(contentForm,InfoContent.class));
		return R.data(baseMapper.getInfoContentDetail(contentForm.getRrId(),contentForm.getTableType()));
	}

	@Override
	public R getInfoContent(Long id) {

		return R.data(BeanUtil.copy(baseMapper.getInfoContent(id),InfoContentDetail.class));
	}

	@Override
	public R deleteByTableTypes(Long rrId, List<Integer> tableTypes) {


		baseMapper.removeByTableTypes(rrId,tableTypes);
		return R.status(true);
	}

	@Override
	public R removeByRrId(Long rrId) {
		baseMapper.removeByRrId(rrId);
		return R.status(true);
	}

	/**
	 * 增加或删除数据时更新对应有无字段的值
	 * @param rrId
	 * @param tableType
	 * @param value
	 */
	public void updateFieldValue(Long rrId,Integer tableType,int value){
		String type = EnumUtil.getTargetValue(RightRegisterConstants.CollectEntityType.class,
				tableType,"getValue","getType");
		if(OWN.equals(type)){
			String propertyName = EnumUtil.getTargetValue(RightRegisterConstants.CollectEntityType.class,
					tableType,"getValue","getField");
			if(!StringUtils.isBlank(propertyName)){
				CollectOwn own = new CollectOwn();
				BeanUtil.setProperty(own,propertyName,value);
				own.setId(rrId);
				collectOwnService.updateById(own);
			}

		}else if (CHANGE.equals(type)){
			String propertyName = EnumUtil.getTargetValue(RightRegisterConstants.CollectEntityType.class,
					tableType,"getValue","getField");
			if(!StringUtils.isBlank(propertyName)) {
				CollectChange change = new CollectChange();
				BeanUtil.setProperty(change, propertyName, value);
				change.setId(rrId);
				collectChangeService.updateById(change);
			}

		}else{
			String propertyName = EnumUtil.getTargetValue(RightRegisterConstants.CollectEntityType.class,
					tableType,"getValue","getField");
			if(!StringUtils.isBlank(propertyName)) {
				CollectDestroy destroy = new CollectDestroy();
				BeanUtil.setProperty(destroy, propertyName, value);
				destroy.setId(rrId);
				collectDestroyService.updateById(destroy);
			}

		}

	}



	/**
	*  字段类型合规性校验
	* @Param: [contentForm]
	* @return: org.springblade.core.tool.api.R
	* @Author: shaojun.li
	* @Date: 2019/12/24
	*/
	public R validTableFieldType(InfoContentForm contentForm){
		Integer tableType = contentForm.getTableType();
		List<InfoTemplateDetail> templateDetails = infoTemplateService.getInfoTemplateDetail(Collections.singletonList(tableType));
		if(templateDetails.isEmpty()){
			return R.fail("请联系管理员确认该表单是否有效或存在");
		}
		//最大列的值
		Integer maxDisplayOrder = templateDetails.stream().max(Comparator.comparingInt(InfoTemplateDetail::getDisplayOrder)).get().getDisplayOrder();
		//校验是否传了多余的参数.目前最大列为12
		for(int i = maxDisplayOrder+1;i <= Integer.parseInt(String.valueOf(RightRegisterConstants.TableFieldType.MAX_COLUMN.getValue()));i++){
			String maxFieldName ="value"+i;
			if(BeanUtil.getProperty(contentForm,maxFieldName)!=null){
				return R.fail("请求包含不合规的参数");
			}
		}
		for(InfoTemplateDetail templateDetail:templateDetails){
			//必填项校验
			if(null!=templateDetail.getIsMustFill()&&templateDetail.getIsMustFill().equals(1)){
				int fieldOrder = templateDetail.getDisplayOrder();
				String fieldName ="value"+fieldOrder;
				if(null==BeanUtil.getProperty(contentForm,fieldName)||"".equals(BeanUtil.getProperty(contentForm,fieldName))){
					return R.fail("备注以外的字段值为必填项");
				}
			}
			//字段类型校验:字符串长度、金额格式、比例格式
			Integer columnType = templateDetail.getColumnType();
			String fieldName = "value" + templateDetail.getDisplayOrder();
			if(RightRegisterConstants.TableFieldType.STRING.getValue().equals(columnType)){
				String value = String.valueOf(BeanUtil.getProperty(contentForm, fieldName));
				if(StringUtils.length(value)>255){
					return R.fail("字段长度不能超过255字符");
				}
			}else if(RightRegisterConstants.TableFieldType.NUMBER.getValue().equals(columnType)){
				String value = String.valueOf(BeanUtil.getProperty(contentForm, fieldName));
				if(!NumberUtil.validNumber(value,9,2,false,true)){
					return R.fail("金额字段值格式有误:最大值为1000000000且最多两位小数的数字");
				}
			}else if(RightRegisterConstants.TableFieldType.SCALE.getValue().equals(columnType)){
				String value = String.valueOf(BeanUtil.getProperty(contentForm,fieldName));
				if(!NumberUtil.validNumber(value,2,2,false,true)){
					return R.fail("比例字段值格式有误:最大值为100且最多两位小数的数字");
				}
				double iFieldValue = Double.parseDouble(value);
				List<InfoContentDetail> infoContentDetails = baseMapper.getInfoContentDetail(contentForm.getRrId(),tableType);
				double valuesTotal=0.00;
				for(InfoContentDetail detail:infoContentDetails){
					if(detail.getId().equals(contentForm.getId())){
						continue;
					}
					valuesTotal =valuesTotal+Double.parseDouble((String) Objects.requireNonNull(BeanUtil.getProperty(detail, fieldName)));
				}
				if((valuesTotal+iFieldValue)>100){
					return R.fail("比例相加不能超过100");
				}
			}else {
				return R.fail("字段类型有误,请联系管理员确认并修正");
			}
		}

		return R.success("校验成功");
	}


}
