/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Caratacus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.baomidou.hibernateplus.dao.impl;

import com.baomidou.framework.entity.Convert;
import com.baomidou.hibernateplus.dao.IDao;
import com.baomidou.hibernateplus.exceptions.HibernatePlusException;
import com.baomidou.hibernateplus.page.CountOptimize;
import com.baomidou.hibernateplus.page.Page;
import com.baomidou.hibernateplus.query.Condition;
import com.baomidou.hibernateplus.query.Wrapper;
import com.baomidou.hibernateplus.utils.CollectionUtils;
import com.baomidou.hibernateplus.utils.EntityInfoUtils;
import com.baomidou.hibernateplus.utils.HibernateUtils;
import com.baomidou.hibernateplus.utils.MapUtils;
import com.baomidou.hibernateplus.utils.ReflectionKit;
import com.baomidou.hibernateplus.utils.SqlUtils;
import com.baomidou.hibernateplus.utils.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.transform.Transformers;
import org.jboss.logging.Logger;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * IDao接口实现类
 * </p>
 *
 * @author Caratacus
 * @date 2016-11-23
 */
public class DaoImpl<T extends Convert, V extends Convert> implements IDao<T, V> {

	private static final Logger logger = Logger.getLogger(DaoImpl.class);
	/* 反射TO泛型 */
	protected Class<T> toCls = null;

	/* 反射VO泛型 */
	protected Class<V> voCls = null;

	/**
	 * 获取SessionFactory 如果需要使用读写分离可以重写该方法
	 *
	 * @return
	 */
	public SessionFactory getSessionFactory() {
		return EntityInfoUtils.getEntityInfo(toClass()).getSessionFactory();
	}

	@Override
	public T get(Serializable id) {
		if (null == id)
			throw new HibernatePlusException("execute Get Fail! Param is Empty !");
		return (T) HibernateUtils.getCurrentSession(getSessionFactory()).get(toClass(), id);
	}

	@Override
	public T get(String property, Object value) {
		List<T> list = selectList(Condition.instance().eq(property, value));
		if (CollectionUtils.isNotEmpty(list)) {
			int size = list.size();
			if (size > 1) {
				logger.warn(String.format("Warn: get Method There are  %s results.", size));
			}
			return list.get(0);
		}
		return null;
	}

	/**
	 * 根据HQL查询单个对象
	 *
	 * @param hql
	 * @return
	 */
	protected T get(String hql) {
		return (T) get(hql, Collections.EMPTY_MAP);
	}

	/**
	 * 根据HQL查询单个对象
	 *
	 * @param hql
	 * @param params
	 * @return
	 */
	protected T get(String hql, Map<String, Object> params) {
		if (StringUtils.isBlank(hql))
			throw new HibernatePlusException("execute Get Fail! Param is Empty !");
		T t = null;
		try {
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
			if (MapUtils.isNotEmpty(params)) {
				for (String key : params.keySet()) {
					Object obj = params.get(key);
					HibernateUtils.setParams(query, key, obj);
				}
			}
			t = (T) query.uniqueResult();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return t;

	}

	@Override
	public T save(T t) {
		if (null == t)
			throw new HibernatePlusException("execute Save Fail! Param is Empty !");
		HibernateUtils.getCurrentSession(getSessionFactory()).save(t);
		return t;
	}

	@Override
	public void saveOrUpdate(T t) {
		if (null == t)
			throw new HibernatePlusException("execute SaveOrUpdate! Param is Empty !");
		HibernateUtils.getCurrentSession(getSessionFactory()).saveOrUpdate(t);
	}

	@Override
	public void update(T t) {
		if (null == t)
			throw new HibernatePlusException("execute Update! Param is Empty !");
		HibernateUtils.getCurrentSession(getSessionFactory()).merge(t);
	}

	@Override
	public int update(Map<String, Object> setMap, Wrapper wrapper) {
		if (MapUtils.isEmpty(setMap))
			throw new HibernatePlusException("execute Update! Param is Empty !");
		String sqlUpdate = SqlUtils.sqlUpdate(toClass(), setMap, wrapper);
		return executeSqlUpdate(sqlUpdate);
	}

	@Override
	public void delete(T t) {
		if (null == t)
			throw new HibernatePlusException("execute Delete! Param is Empty !");
		HibernateUtils.getCurrentSession(getSessionFactory()).delete(t);
	}

	@Override
	public int delete(Wrapper wrapper) {
		String sqlDelete = SqlUtils.sqlDelete(toClass(), wrapper);
		return executeSqlUpdate(sqlDelete);

	}

	@Override
	public boolean insertBatch(List<T> list, int size) {
		if (CollectionUtils.isEmpty(list))
			throw new HibernatePlusException("execute BatchInsert Fail! Param is Empty !");
		try {
			Session session = HibernateUtils.getCurrentSession(getSessionFactory());
			for (int i = 0; i < list.size(); i++) {
				session.save(list.get(i));
				if (i % size == 0) {
					session.flush();
					session.clear();
				}
			}
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
			return false;
		}
		return true;
	}

	@Override
	public boolean updateBatch(List<T> list, int size) {
		if (CollectionUtils.isEmpty(list))
			throw new HibernatePlusException("execute BatchUpdate Fail! Param is Empty !");
		try {
			Session session = HibernateUtils.getCurrentSession(getSessionFactory());
			for (int i = 0; i < list.size(); i++) {
				session.update(list.get(i));
				if (i % size == 0) {
					session.flush();
					session.clear();
				}
			}
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
			return false;
		}
		return true;

	}

	@Override
	public <T> List<T> selectList(Wrapper wrapper) {
		List<T> list = Collections.emptyList();
		try {
			String sql = SqlUtils.sqlEntityList(toClass(), wrapper, null);
			Query query = HibernateUtils.getEntitySqlQuery(toClass(), sql, getSessionFactory());
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	@Override
	public List<Map<String, Object>> selectMaps(Wrapper wrapper) {
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			String sql = SqlUtils.sqlList(toClass(), wrapper, null);
			Query query = HibernateUtils.getEntitySqlQuery(toClass(), sql, getSessionFactory());
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	@Override
	public int selectCount() {
		return selectCount(Collections.EMPTY_MAP);
	}

	@Override
	public int selectCount(String property, Object... value) {
		return selectCount(new String[] { property }, value);
	}

	@Override
	public int selectCount(String[] property, Object... value) {
		String countHql = HibernateUtils.getCountHql(toClass(), property);
		Query query = HibernateUtils.getHqlQuery(countHql, getSessionFactory());
		for (int i = 0; i < value.length; i++) {
			HibernateUtils.setParams(query, StringUtils.toString(i), value[i]);
		}
		return (Integer) query.uniqueResult();
	}

	@Override
	public int selectCount(Map<String, Object> params) {
		String hql = HibernateUtils.getCountHql(toClass(), params);
		Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
		if (MapUtils.isNotEmpty(params)) {
			for (String key : params.keySet()) {
				Object obj = params.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
		}
		return (Integer) query.uniqueResult();

	}

	@Override
	public int selectCount(Wrapper wrapper) {
		int count = 0;
		try {
			String sql = SqlUtils.sqlCount(toClass(), wrapper);
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
			BigInteger bigInteger = (BigInteger) query.uniqueResult();
			count = bigInteger.intValue();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return count;
	}

	@Override
	public Page<Map<String, Object>> selectMapPage(Wrapper wrapper, Page<Map<String, Object>> page) {
		try {
			String sql = SqlUtils.sqlList(toClass(), wrapper, page);
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			HibernateUtils.setPage(page.getCurrent(), page.getSize(), query);
			page.setRecords(query.list());
			CountOptimize countOptimize = SqlUtils.getCountOptimize(sql, page.isOptimizeCount());
			Query countQuery = HibernateUtils.getSqlQuery(countOptimize.getCountSQL(), getSessionFactory());
			BigInteger bigInteger = (BigInteger) countQuery.uniqueResult();
			page.setTotal(bigInteger.intValue());
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return page;
	}

	@Override
	public Page selectPage(Wrapper wrapper, Page page) {
		try {
			String sql = SqlUtils.sqlList(toClass(), wrapper, page);
			Query query = HibernateUtils.getEntitySqlQuery(toClass(), sql, getSessionFactory());
			HibernateUtils.setPage(page.getCurrent(), page.getSize(), query);
			page.setRecords(query.list());
			CountOptimize countOptimize = SqlUtils.getCountOptimize(sql, page.isOptimizeCount());
			Query countQuery = HibernateUtils.getSqlQuery(countOptimize.getCountSQL(), getSessionFactory());
			BigInteger bigInteger = (BigInteger) countQuery.uniqueResult();
			page.setTotal(bigInteger.intValue());
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return page;
	}

	/**
	 * 查询列表
	 *
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(String property, Object value) {
		return query(0, 0, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(String[] property, Object... value) {
		return query(0, 0, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(int page, int rows, String property, Object value) {
		return query(page, rows, StringUtils.EMPTY_STRING, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(int page, int rows, String[] property, Object... value) {
		return query(0, 0, StringUtils.EMPTY_STRING, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param order
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(String order, String property, Object value) {
		return query(0, 0, order, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param order
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(String order, String[] property, Object... value) {
		return query(0, 0, order, property, value);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param order
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(int page, int rows, String order, String property, Object value) {
		return query(page, rows, order, new String[] { property }, value);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param order
	 * @param property
	 * @param value
	 * @return
	 */
	public List<T> query(int page, int rows, String order, String[] property, Object... value) {
		List<T> list = Collections.emptyList();
		try {
			String hql = HibernateUtils.getListHql(order, toClass(), property);
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
			if (null != value) {
				for (int i = 0; i < value.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), value[i]);
				}
			}
			HibernateUtils.setPage(page, rows, query);
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 查询列表
	 *
	 * @param order
	 * @return
	 */
	public List<T> query(String order) {
		return query(Collections.EMPTY_MAP, order);
	}

	/**
	 * 查询列表
	 *
	 * @param order
	 * @param page
	 * @param rows
	 * @return
	 */
	public List<T> query(String order, int page, int rows) {
		return query(page, rows, Collections.EMPTY_MAP, order);
	}

	/**
	 * 查询列表
	 *
	 * @return
	 */
	public List<T> query() {
		return query(Collections.EMPTY_MAP);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @return
	 */
	public List<T> query(int page, int rows) {
		return query(page, rows, Collections.EMPTY_MAP, null);
	}

	/**
	 * 查询列表
	 *
	 * @param params
	 * @return
	 */
	public List<T> query(Map<String, Object> params) {
		return query(params, StringUtils.EMPTY_STRING);

	}

	/**
	 * 查询列表
	 *
	 * @param params
	 * @param order
	 * @return
	 */
	public List<T> query(Map<String, Object> params, String order) {
		return query(0, 0, params, order);
	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param params
	 * @param order
	 * @return
	 */
	public List<T> query(int page, int rows, Map<String, Object> params, String order) {
		List<T> list = Collections.emptyList();
		try {
			String hql = HibernateUtils.getListHql(order, toClass(), params);
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
			setParamMap(params, query);
			HibernateUtils.setPage(page, rows, query);
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;

	}

	/**
	 * 查询列表
	 *
	 * @param page
	 * @param rows
	 * @param map
	 * @return
	 */
	public List<T> query(int page, int rows, Map<String, Object> map) {
		return query(page, rows, map, StringUtils.EMPTY_STRING);
	}

	/**
	 * 执行HQL
	 *
	 * @param hql
	 * @return
	 */
	protected int executeHql(String hql) {
		return executeHql(hql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行HQL
	 *
	 * @param hql
	 * @param params
	 * @return
	 */
	protected int executeHql(String hql, Map<String, Object> params) {
		if (StringUtils.isBlank(hql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
		if (MapUtils.isNotEmpty(params)) {
			for (String key : params.keySet()) {
				Object obj = params.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
		}
		return query.executeUpdate();
	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @return
	 */
	protected List<T> queryListWithHql(String hql) {
		return queryListWithHql(hql, Collections.EMPTY_MAP);
	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @param params
	 * @return
	 */
	protected List<T> queryListWithHql(String hql, Map<String, Object> params) {
		return queryListWithHql(hql, params, 0, 0);
	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @param params
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List<T> queryListWithHql(String hql, Map<String, Object> params, int page, int rows) {
		if (StringUtils.isBlank(hql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List<T> list = Collections.emptyList();
		try {
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
			setParamMap(params, query);
			HibernateUtils.setPage(page, rows, query);
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;

	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List<T> queryListWithHql(String hql, int page, int rows) {
		return queryListWithHql(hql, Collections.EMPTY_MAP, page, rows);
	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithHql(String hql) {
		return queryMapsWithHql(hql, 0, 0);
	}

	/**
	 * 根据HQL查询列表
	 *
	 * @param hql
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithHql(String hql, int page, int rows) {
		if (StringUtils.isBlank(hql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			HibernateUtils.setPage(page, rows, query);
			list = query.list();

		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 根据HQL查询数量
	 *
	 * @param hql
	 * @return
	 */
	protected int queryCountWithHql(String hql) {
		return queryCountWithHql(hql, Collections.EMPTY_MAP);
	}

	/**
	 * 根据HQL查询数量
	 *
	 * @param hql
	 * @param params
	 * @return
	 */
	protected int queryCountWithHql(String hql, Map<String, Object> params) {
		if (StringUtils.isBlank(hql))
			throw new HibernatePlusException("Query Count Fail! Param is Empty !");
		Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory());
		if (MapUtils.isNotEmpty(params)) {
			for (String key : params.keySet()) {
				Object obj = params.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
		}
		BigInteger bigInteger = (BigInteger) query.uniqueResult();
		return bigInteger.intValue();
	}

	/**
	 * 执行SQL
	 *
	 * @param sql
	 * @return
	 */
	protected int executeSqlUpdate(String sql) {
		return executeSqlUpdate(sql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行SQL
	 *
	 * @param sql
	 * @param args
	 * @return
	 */
	protected int executeSqlUpdate(String sql, Object[] args) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		int resultCount = 0;
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
			if (null != args) {
				for (int i = 0; i < args.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), args[i]);
				}
			}
			resultCount = query.executeUpdate();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return resultCount;
	}

	/**
	 * 执行SQL
	 *
	 * @param sql
	 * @param params
	 * @return
	 */
	protected int executeSqlUpdate(String sql, Map<String, Object> params) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		int resultCount = 0;
		if (StringUtils.isNotBlank(sql)) {
			try {
				Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
				if ((params != null) && !params.isEmpty()) {
					for (String key : params.keySet()) {
						Object obj = params.get(key);
						HibernateUtils.setParams(query, key, obj);
					}
				}
				resultCount = query.executeUpdate();
			} catch (Exception e) {
				logger.warn("Warn: Unexpected exception.  Cause:" + e);
			}
		}
		return resultCount;
	}

	/**
	 * 执行SQL查询数量
	 *
	 * @param sql
	 * @return
	 */
	protected int queryCountWithSql(String sql) {
		return queryCountWithSql(sql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行SQL查询数量
	 *
	 * @param sql
	 * @param params
	 * @return
	 */
	protected int queryCountWithSql(String sql, Map<String, Object> params) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
		if (MapUtils.isNotEmpty(params)) {
			for (String key : params.keySet()) {
				Object obj = params.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
		}
		BigInteger count = (BigInteger) query.uniqueResult();
		return count.intValue();
	}

	/**
	 * 执行SQL返回单个对象
	 *
	 * @param sql
	 * @param params
	 * @return
	 */
	protected Map<String, Object> queryMapWithSql(String sql, Map<String, Object> params) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		Map<String, Object> map = Collections.emptyMap();
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			if (MapUtils.isNotEmpty(params)) {
				for (String key : params.keySet()) {
					Object obj = params.get(key);
					HibernateUtils.setParams(query, key, obj);
				}
			}
			map = (Map<String, Object>) query.uniqueResult();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return map;
	}

	/**
	 * 执行SQL返回单个对象
	 *
	 * @param sql
	 * @return
	 */
	protected Map<String, Object> queryMapWithSql(String sql) {
		return queryMapWithSql(sql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param args
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithSql(String sql, Object[] args) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			if (null != args) {
				for (int i = 0; i < args.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), args[i]);
				}
			}
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithSql(String sql) {
		return queryMapsWithSql(sql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithSql(String sql, int page, int rows) {
		return queryMapsWithSql(sql, Collections.EMPTY_MAP, page, rows);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param params
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithSql(String sql, Map<String, Object> params, int page, int rows) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			if (MapUtils.isNotEmpty(params)) {
				for (String key : params.keySet()) {
					Object obj = params.get(key);
					HibernateUtils.setParams(query, key, obj);
				}
			}
			HibernateUtils.setPage(page, rows, query);
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param params
	 * @return
	 */
	protected List<Map<String, Object>> queryMapsWithSql(String sql, Map<String, Object> params) {
		return queryMapsWithSql(sql, params, 0, 0);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param args
	 * @return
	 */
	protected List queryListWithSql(String sql, Object[] args) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List list = Collections.EMPTY_LIST;
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
			if (null != args) {
				for (int i = 0; i < args.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), args[i]);
				}
			}
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @return
	 */
	protected List queryListWithSql(String sql) {
		return this.queryListWithSql(sql, Collections.EMPTY_MAP);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List queryListWithSql(String sql, int page, int rows) {
		return this.queryListWithSql(sql, Collections.EMPTY_MAP, page, rows);
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param params
	 * @param page
	 * @param rows
	 * @return
	 */
	protected List queryListWithSql(String sql, Map<String, Object> params, int page, int rows) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		List list = Collections.emptyList();
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory());
			if (MapUtils.isNotEmpty(params)) {
				for (String key : params.keySet()) {
					Object obj = params.get(key);
					HibernateUtils.setParams(query, key, obj);
				}
			}
			HibernateUtils.setPage(page, rows, query);
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;
	}

	/**
	 * 执行SQL查询列表
	 *
	 * @param sql
	 * @param params
	 * @return
	 */
	protected List queryListWithSql(String sql, Map<String, Object> params) {
		return this.queryListWithSql(sql, params, 0, 0);
	}

	/**
	 * 执行SQL返回单个对象
	 *
	 * @param sql
	 * @param args
	 * @return
	 */
	protected Map<String, Object> queryMapWithSql(String sql, Object[] args) {
		if (StringUtils.isBlank(sql))
			throw new HibernatePlusException("execute Query Fail! Param is Empty !");
		Map<String, Object> map = Collections.emptyMap();
		try {
			Query query = HibernateUtils.getSqlQuery(sql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			if (null != args) {
				for (int i = 0; i < args.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), args[i]);
				}
			}
			map = (Map<String, Object>) query.uniqueResult();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return map;
	}

	/**
	 * 查询列表
	 *
	 * @param property
	 * @param value
	 * @return
	 */
	public List<Map<String, Object>> queryMapsWithClass(String property, Object value) {
		return queryMapsWithClass(new String[] { property }, value);
	}

	/**
	 * 查询列表
	 *
	 * @param property
	 * @param value
	 * @return
	 */
	public List<Map<String, Object>> queryMapsWithClass(String[] property, Object... value) {
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			String hql = HibernateUtils.getListHql(toClass(), property);
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			if (null != value) {
				for (int i = 0; i < value.length; i++) {
					HibernateUtils.setParams(query, StringUtils.toString(i), value);
				}
			}
			list = query.list();

		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;

	}

	/**
	 * 查询列表
	 *
	 * @param map
	 * @return
	 */

	public List<Map<String, Object>> queryMapWithClass(Map<String, Object> map) {
		List<Map<String, Object>> list = Collections.emptyList();
		try {
			String hql = HibernateUtils.getListHql(toClass(), map);
			Query query = HibernateUtils.getHqlQuery(hql, getSessionFactory()).setResultTransformer(
					Transformers.ALIAS_TO_ENTITY_MAP);
			for (String key : map.keySet()) {
				Object obj = map.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
			list = query.list();
		} catch (Exception e) {
			logger.warn("Warn: Unexpected exception.  Cause:" + e);
		}
		return list;

	}

	/**
	 * Query设置Map参数
	 *
	 * @param params
	 * @param query
	 * @return
	 * @throws
	 * @author Caratacus
	 * @date 2016/9/2 0002
	 * @version 1.0
	 */
	private void setParamMap(Map<String, Object> params, Query query) {
		if (MapUtils.isNotEmpty(params)) {
			for (String key : params.keySet()) {
				Object obj = params.get(key);
				HibernateUtils.setParams(query, key, obj);
			}
		}
	}

	/**
	 * 获取toClass
	 *
	 * @return
	 */
	protected Class<T> toClass() {
		if (toCls == null)
			toCls = ReflectionKit.getSuperClassGenricType(getClass(), 0);
		return toCls;
	}

	/**
	 * 获取voClass
	 *
	 * @return
	 */
	protected Class<V> voClass() {
		if (voCls == null) {
			voCls = ReflectionKit.getSuperClassGenricType(getClass(), 1);
		}
		return voCls;
	}

}
