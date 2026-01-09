package com.a4b.dqes.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;

import javax.sql.DataSource;

import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryBuilderCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.SchemaManagementProvider;
import org.springframework.boot.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.a4b.core.ezmt.client.config.EzmtSecurityDataConfiguration;
import com.a4b.core.multi_ds.boot.autoconfigure.jdbc.MultipleDataSourceConfigurationFactory;
import com.a4b.core.multi_ds.boot.autoconfigure.jdbc.MultipleDataSourceConfigurationFactoryImpl;
import com.a4b.core.multi_ds.boot.autoconfigure.jdbc.MultipleDataSourceProperties;
import com.a4b.core.multi_ds.boot.autoconfigure.orm.jpa.HibernateJpaConfigurationFactory;
import com.a4b.core.multi_ds.boot.autoconfigure.orm.jpa.MultipleJpaProperties;
import com.a4b.dqes.security.SecurityUtils;

import jakarta.persistence.EntityManagerFactory;

/**
 * @author sonnd
 *
 */
@Configuration
@AutoConfigureAfter(DatabaseConfiguration.class)
@EnableJpaRepositories(basePackages = ComDatabaseConfiguration.REPOSITORY_PACKAGE,
		entityManagerFactoryRef = ComDatabaseConfiguration.ENTITY_MANAGER_BEAN_NAME,
		transactionManagerRef = ComDatabaseConfiguration.TRANSACTION_MANAGER_BEAN_NAME)
@EnableTransactionManagement
@Import({ MultipleDataSourceConfigurationFactoryImpl.TomcatDataSourceFactory.class,
		MultipleDataSourceConfigurationFactoryImpl.HikariDataSourceFactory.class,
		MultipleDataSourceConfigurationFactoryImpl.Dbcp2DataSourceFactory.class,
		MultipleDataSourceConfigurationFactoryImpl.Generic.class })

public class ComDatabaseConfiguration {

	public static final String DATASOURCE_NAME = "com";
    public static final String JDBC_TEMPLATE = DATASOURCE_NAME + "JdbcTemplate";
	public static final String NAMED_JDBC_TEMPLATE = DATASOURCE_NAME + "NamedJdbcTemplate";
	public static final String PERSISTENCE_UNIT = DATASOURCE_NAME + "PU";
	public static final String PERSISTENCE_UNIT_XA = DATASOURCE_NAME + "XAPU";
	public static final String DATASOURCE_BEAN_NAME = DATASOURCE_NAME + "DS";
	public static final String DATASOURCE_PROPERTIES_BEAN_NAME = DATASOURCE_NAME + "DataSourceProperties";
	public static final String JPA_PROPERTIES_BEAN_NAME = DATASOURCE_NAME + "JpaProperties";
	public static final String DATASOURCE_XA_BEAN_NAME = DATASOURCE_NAME + "XADS";
	public static final String ENTITY_MANAGER_BEAN_NAME = DATASOURCE_NAME + "EntityManager";
	public static final String TRANSACTION_MANAGER_BEAN_NAME = DATASOURCE_NAME + "TransactionManager";
	public static final String JPA_CONFIGURATION_FACTORY_BEAN_NAME = DATASOURCE_NAME + "JpaConfigurationFactory";
	public static final String JPA_VENDOR_ADAPTER_BEAN_NAME = DATASOURCE_NAME + "JpaVendorAdapter";
	public static final String ENTITY_MANAGER_FACTORY_BUILDER_BEAN_NAME = DATASOURCE_NAME + "EntityManagerFactoryBuilder";
	public static final String DATASOURCE_CONFIGURATION_PREFIX = "datasource." + DATASOURCE_NAME;
	public static final String DATASOURCE_VENDOR_PROPERTIES_CONFIGURATION_PREFIX = DATASOURCE_CONFIGURATION_PREFIX + ".vendor-properties";
	public static final String JPA_CONFIGURATION_PREFIX = "spring.jpa."+ DATASOURCE_NAME;
	public static final String[] DATAMODEL_PACKAGE = { "com.a4b.dqes.domain", "com.a4b.dqes.agg",
			"org.axonframework.modelling.saga.repository.jpa", "org.axonframework.eventhandling.saga.repository.jpa",
			"org.axonframework.eventsourcing.eventstore.jpa", "org.axonframework.eventhandling.tokenstore.jpa",
			EzmtSecurityDataConfiguration.DATAMODEL_PACKAGE};
	public static final String REPOSITORY_PACKAGE = "com.a4b.dqes.repository";

	@Bean(name = DATASOURCE_PROPERTIES_BEAN_NAME)
	@ConfigurationProperties(prefix = DATASOURCE_CONFIGURATION_PREFIX)
	@Primary
	public MultipleDataSourceProperties dataSourceProperties() {
		return new MultipleDataSourceProperties();
	}

	@Bean(name = DATASOURCE_BEAN_NAME)
	@ConfigurationProperties(prefix = DATASOURCE_VENDOR_PROPERTIES_CONFIGURATION_PREFIX)
	@Primary
	public DataSource loadingDataSource(
			@Qualifier(DATASOURCE_PROPERTIES_BEAN_NAME) MultipleDataSourceProperties dataSourceProperties,
			MultipleDataSourceConfigurationFactory dataSourceConfigurationFactory) {
		return dataSourceConfigurationFactory.createDataSource(dataSourceProperties);
	}

	@Bean(name = JPA_PROPERTIES_BEAN_NAME)
	@ConfigurationProperties(prefix = JPA_CONFIGURATION_PREFIX)
	@Primary
	public MultipleJpaProperties jpaProperties() {
		return new MultipleJpaProperties();
	}

	@Bean(name = JPA_CONFIGURATION_FACTORY_BEAN_NAME)
	@Primary
	public HibernateJpaConfigurationFactory jpaConfigurationFactory(
			@Qualifier(DATASOURCE_BEAN_NAME) DataSource dataSource,
			@Qualifier(JPA_PROPERTIES_BEAN_NAME) MultipleJpaProperties jpaProperties,
			ConfigurableListableBeanFactory beanFactory,
			ObjectProvider<Collection<DataSourcePoolMetadataProvider>> metadataProviders,
			ObjectProvider<SchemaManagementProvider> providers,
			ObjectProvider<PhysicalNamingStrategy> physicalNamingStrategy,
			ObjectProvider<ImplicitNamingStrategy> implicitNamingStrategy,
			ObjectProvider<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
		HibernateProperties hp = new HibernateProperties();
		hp.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings());
		return new HibernateJpaConfigurationFactory(dataSource, jpaProperties, beanFactory, null, null, hp,
				metadataProviders, providers, physicalNamingStrategy, implicitNamingStrategy,
				hibernatePropertiesCustomizers);
	}

	@Bean(name = JPA_VENDOR_ADAPTER_BEAN_NAME)
	@Primary
	public JpaVendorAdapter jpaVendorAdapter(
			@Qualifier(JPA_CONFIGURATION_FACTORY_BEAN_NAME) HibernateJpaConfigurationFactory jpaConfigurationFactory) {
		return jpaConfigurationFactory.jpaVendorAdapter();
	}

	@Bean(name = ENTITY_MANAGER_FACTORY_BUILDER_BEAN_NAME)
	@Primary
	public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
			@Qualifier(JPA_CONFIGURATION_FACTORY_BEAN_NAME) HibernateJpaConfigurationFactory jpaConfigurationFactory,
			JpaVendorAdapter jpaVendorAdapter, ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
			ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers) {
		return jpaConfigurationFactory.entityManagerFactoryBuilder(jpaVendorAdapter, persistenceUnitManager, customizers);
	}

	@Bean(name = { ENTITY_MANAGER_BEAN_NAME, "entityManagerFactory" })
	@Primary
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			@Qualifier(JPA_CONFIGURATION_FACTORY_BEAN_NAME) HibernateJpaConfigurationFactory jpaConfigurationFactory,
			@Qualifier(ENTITY_MANAGER_FACTORY_BUILDER_BEAN_NAME) EntityManagerFactoryBuilder factoryBuilder) {
		EntityManagerFactoryBuilder.Builder builder = jpaConfigurationFactory.entityManagerFactory(factoryBuilder)
				.packages(DATAMODEL_PACKAGE).persistenceUnit(PERSISTENCE_UNIT);
		return builder.build();
	}

	@Primary
	@Bean(name = TRANSACTION_MANAGER_BEAN_NAME)
	public PlatformTransactionManager transactionManager(
			@Qualifier(ENTITY_MANAGER_BEAN_NAME) EntityManagerFactory entityManagerFactory,
			@Qualifier(DATASOURCE_BEAN_NAME) DataSource dataSource) {
		return new JpaTransactionManager(entityManagerFactory){
			@Override
			protected void doBegin(Object transaction, TransactionDefinition definition) {
				super.doBegin(transaction, definition);

				// safety: only set when TX is active
      			// if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

				Connection con = DataSourceUtils.getConnection(dataSource);
				setLocal(con, "user.tenant_code", safe(tenantCode(), null));
				setLocal(con, "user.app_code", safe(appCode(), null));
				setLocal(con, "user.user_id", safe(username(), null));
				setLocal(con, "user.rls_mode", rlsMode());
			}
		};
	}

	@Bean(name = JDBC_TEMPLATE)
	@Primary
	public JdbcTemplate jdbcTemplate( @Qualifier(DATASOURCE_BEAN_NAME) DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean(name = NAMED_JDBC_TEMPLATE)
	@Primary
	public NamedParameterJdbcTemplate namedParameterJdbcTemplate( @Qualifier(DATASOURCE_BEAN_NAME) DataSource dataSource) {
		return new NamedParameterJdbcTemplate(dataSource);
	}

	private void setLocal(Connection con, String key, String value) {
		try (PreparedStatement ps = con.prepareStatement("select set_config(?, ?, true)")) {
			ps.setString(1, key);
			ps.setString(2, value);
			ps.execute();
		} catch (Exception e) {
			throw new RuntimeException("Failed to set GUC " + key, e);
		}
	}

	private static String safe(String v, String fallback) {
		return (v == null || v.isBlank()) ? fallback : v;
	}

	private String tenantCode() {
		return SecurityUtils.getCurrentUserTenantCode();
	}

	private String appCode() {
		return SecurityUtils.getCurrentAppCode();
	}

	private String username() {
		return SecurityUtils.getCurrentUserLogin().orElseGet(() -> "anonymous");
	}

	private String rlsMode() {
		return "BYPASSRLS";
	}
}
