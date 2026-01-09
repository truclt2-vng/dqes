/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.util;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.a4b.core.server.annotation.ControllingField;
import com.a4b.core.server.annotation.UnmodifiableField;
import com.a4b.core.server.dto.PropertyDto;
import com.a4b.dqes.constant.Constants;
import com.a4b.dqes.constant.DataType;

import jakarta.validation.constraints.NotNull;


public class ExtraInfoUtil {

	public static String detectObjectDataType(Object val) {
		if (val == null) {
			return null;
		}

		if (val instanceof Boolean) {
			return DataType.BOOLEAN;
		} else if (val instanceof Date) {
			return DataType.DATE;
		} else if (val instanceof Number) {
			return DataType.NUMERIC;
		} else if (val instanceof String) {
			return DataType.STRING;
		} else if (val instanceof Collection) {
			return DataType.JSON_ARRAY;
		} else {
			return DataType.JSON_OBJECT;
		}
	}

	public static boolean validateValue(String validationExpression, Object value, Object payload) {
		try {
			ExpressionParser expressionParser = new SpelExpressionParser();
			Expression expression = expressionParser.parseExpression(validationExpression);
			EvaluationContext context = new StandardEvaluationContext();
			context.setVariable("value", value);
			context.setVariable("payload", payload);
			Object result = expression.getValue(context);
			return Boolean.TRUE.equals(result);
		} catch (ParseException ex) {
			return false;
		}
	}

	public static Map<String, PropertyDto> extractEntityProperties(Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Map<String, PropertyDto> properties = new HashMap<>();
		for (Field field : fields) {
			int modifiers = field.getModifiers();
			if (Modifier.isFinal(modifiers)) {
				continue;
			}
			ControllingField controllingFieldAnno = AnnotationUtils.getAnnotation(field, ControllingField.class);
			if (controllingFieldAnno != null) {
				continue;
			}
			NotNull notNullAnno = AnnotationUtils.getAnnotation(field, NotNull.class);
			UnmodifiableField unmodifiableFieldAnno = AnnotationUtils.getAnnotation(field, UnmodifiableField.class);
			Boolean required = notNullAnno != null;
			boolean pivotable = isPivotable(clazz.getName(), field.getName());
			boolean allowAgg = isMeasurable(field.getType());
			boolean editable = (unmodifiableFieldAnno == null);
			boolean groupable = pivotable;
			boolean securedField = isSecuredField(clazz.getName(), field.getName());
			PropertyDto prop = new PropertyDto(field.getName(), getDataType(field), required, pivotable, allowAgg,
					editable, groupable, securedField, null);
			properties.put(field.getName(), prop);
		}

		return properties;
	}

	public static Map<String, PropertyDto> extractEntityPropertiesPivot(Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Map<String, PropertyDto> properties = new HashMap<>();
		for (Field field : fields) {
			int modifiers = field.getModifiers();
			if (Modifier.isFinal(modifiers)) {
				continue;
			}
			String dataType = getDataType(field);
			if (DataType.JSON_ARRAY.equals(dataType) || DataType.JSON_OBJECT.equals(dataType)) {
				continue;
			}

			NotNull notNullAnno = AnnotationUtils.getAnnotation(field, NotNull.class);
			UnmodifiableField unmodifiableFieldAnno = AnnotationUtils.getAnnotation(field, UnmodifiableField.class);
			Boolean required = notNullAnno != null;
			boolean pivotable = isPivotable(clazz.getName(), field.getName());
			boolean allowAgg = isMeasurable(field.getType());
			boolean editable = (unmodifiableFieldAnno == null);
			boolean groupable = pivotable;
			boolean securedField = isSecuredField(clazz.getName(), field.getName());
			PropertyDto prop = new PropertyDto(/* columnName */field.getName(), dataType, required, pivotable, allowAgg,
					editable, groupable, securedField, null);
			properties.put(field.getName(), prop);
		}

		return properties;
	}

	public static boolean isPivotable(String entityClassName, String fieldName) {
		return !isSecuredField(entityClassName, fieldName);
	}

	public static boolean isSecuredField(String entityClassName, String fieldName) {
		return false;
	}

	public static boolean isMeasurable(Class fieldType) {
		return Number.class.isAssignableFrom(fieldType);
	}

	private static String getDataType(Field field) {
		if (String.class.isAssignableFrom(field.getType())) {
			return DataType.STRING;
		} else if (Number.class.isAssignableFrom(field.getType())) {
			return DataType.NUMERIC;
		} else if (Date.class.isAssignableFrom(field.getType())) {
			return DataType.DATE;
		} else if (Boolean.class.isAssignableFrom(field.getType())) {
			return DataType.BOOLEAN;
		} else if (Collection.class.isAssignableFrom(field.getType())) {
			return DataType.JSON_ARRAY;
		} else {
			return DataType.JSON_OBJECT;
		}
	}

	public static String getClassName(String dataType) {
		if (DataType.STRING.equals(dataType)) {
			return String.class.getName();
		} else if (DataType.NUMERIC.equals(dataType)) {
			return Number.class.getName();
		} else if (DataType.DATE.equals(dataType)) {
			return Date.class.getName();
		} else if (DataType.BOOLEAN.equals(dataType)) {
			return Boolean.class.getName();
		} else if (DataType.JSON_ARRAY.equals(dataType)) {
			return ArrayList.class.getName();
		} else if (DataType.JSON_OBJECT.equals(dataType)) {
			return HashMap.class.getName();
		}

		return null;
	}

	public static Class getDataClass(String dataType) {
		if (DataType.STRING.equals(dataType)) {
			return String.class;
		} else if (DataType.NUMERIC.equals(dataType)) {
			return Number.class;
		} else if (DataType.DATE.equals(dataType)) {
			return Date.class;
		} else if (DataType.BOOLEAN.equals(dataType)) {
			return Boolean.class;
		} else if (DataType.JSON_ARRAY.equals(dataType)) {
			return ArrayList.class;
		} else if (DataType.JSON_OBJECT.equals(dataType)) {
			return HashMap.class;
		}

		return null;
	}

	public static String getValueColumnName(String dataType) {
		if (DataType.STRING.equals(dataType)) {
			return "value_string";
		} else if (DataType.NUMERIC.equals(dataType)) {
			return "value_numeric";
		} else if (DataType.DATE.equals(dataType)) {
			return "value_date";
		} else if (DataType.BOOLEAN.equals(dataType)) {
			return "value_numeric";
		} else if (DataType.JSON_ARRAY.equals(dataType)) {
			return "value_json";
		} else if (DataType.JSON_OBJECT.equals(dataType)) {
			return "value_json";
		}

		return null;
	}

	public static <T> T mapToDto(Map<String, Object> map, Class<T> clazz) {
		try {
			T obj = clazz.newInstance();
			Map<String, Object> extraInfo = new HashMap<>();
			for (Entry<String, Object> entry : map.entrySet()) {
				String propName = entry.getKey();
				Object value = entry.getValue();

				if (propName.startsWith(Constants.EXTRA_INFO_FILTER_PREFIX)) {
					extraInfo.put(propName.substring(Constants.EXTRA_INFO_FILTER_PREFIX.length()), value);
				} else {
					setPropertyValue(obj, propName, value);
				}
			}
			if (PropertyUtils.isWriteable(obj, "extraInfo")) {
				PropertyUtils.setProperty(obj, "extraInfo", extraInfo);
			}
			return obj;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to convert data", ex);
		}
	}

	private static void setPropertyValue(Object obj, String propName, Object value)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		if (PropertyUtils.isWriteable(obj, propName)) {
			Object val = null;
			if (value instanceof Timestamp) {
				val = new Date(((Timestamp) value).getTime());
			} else if (value instanceof BigDecimal) {
				Class clazz = PropertyUtils.getPropertyType(obj, propName);
				if (Long.class.isAssignableFrom(clazz)) {
					val = ((BigDecimal) value).longValue();
				} else if (Integer.class.isAssignableFrom(clazz)) {
					val = ((BigDecimal) value).intValue();
				} else if (Short.class.isAssignableFrom(clazz)) {
					val = ((BigDecimal) value).shortValue();
				} else {
					val = value;
				}
			} else {
				val = value;
			}
			PropertyUtils.setProperty(obj, propName, val);
		}
	}

	public static <T> Map<String, Object> dtoToMap(T obj) {
		try {
			Map<String, Object> map = new HashMap<>();
			PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(obj);
			for (PropertyDescriptor pd : propertyDescriptors) {
				String propName = pd.getName();
				Object val = PropertyUtils.getProperty(obj, propName);
				if (propName.equals(Constants.EXTRA_INFO_PROP)) {
					if (val instanceof Map) {
						Map<String, Object> extraInfo = (Map<String, Object>) val;
						for (Entry<String, Object> entry : extraInfo.entrySet()) {
							map.put(Constants.EXTRA_INFO_FILTER_PREFIX + entry.getKey(), entry.getValue());
						}
					}
				} else {
					map.put(propName, val);
				}
			}
			return map;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to convert data", ex);
		}
	}
}
