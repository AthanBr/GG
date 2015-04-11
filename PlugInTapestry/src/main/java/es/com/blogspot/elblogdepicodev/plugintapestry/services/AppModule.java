package es.com.blogspot.elblogdepicodev.plugintapestry.services;

import java.util.Date;
import java.util.UUID;

import javax.servlet.ServletContext;

import org.apache.shiro.realm.Realm;
import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.annotations.Path;
import org.apache.tapestry5.beanvalidator.BeanValidatorConfigurer;
import org.apache.tapestry5.hibernate.HibernateSessionSource;
import org.apache.tapestry5.ioc.Configuration;
import org.apache.tapestry5.ioc.MappedConfiguration;
import org.apache.tapestry5.ioc.OrderedConfiguration;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Local;
import org.apache.tapestry5.ioc.services.ClasspathURLConverter;
import org.apache.tapestry5.services.javascript.JavaScriptModuleConfiguration;
import org.apache.tapestry5.services.javascript.JavaScriptStack;
import org.apache.tapestry5.services.transform.ComponentClassTransformWorker2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.tynamo.security.SecuritySymbols;
import org.tynamo.shiro.extension.realm.text.ExtendedPropertiesRealm;

import es.com.blogspot.elblogdepicodev.plugintapestry.misc.ContextListener;
import es.com.blogspot.elblogdepicodev.plugintapestry.misc.DateTranslator;
import es.com.blogspot.elblogdepicodev.plugintapestry.misc.PlugInStack;
import es.com.blogspot.elblogdepicodev.plugintapestry.misc.WildFlyClasspathURLConverter;
import es.com.blogspot.elblogdepicodev.plugintapestry.services.hibernate.HibernateSessionSourceImpl;
import es.com.blogspot.elblogdepicodev.plugintapestry.services.workers.CsrfWorker;

public class AppModule {

	private static final Logger logger = LoggerFactory.getLogger(AppModule.class);

	public static final String CDN_DOMAIN_PROTOCOL = "cdn.protocol";
	public static final String CDN_DOMAIN_HOST = "cdn.host";
	public static final String CDN_DOMAIN_PORT = "cdn.port";
	public static final String CDN_DOMAIN_PATH = "cdn.path";

	public static void bind(ServiceBinder binder) {
		// Añadir al contenedor de dependencias nuestros servicios, se proporciona la interfaz y la
		// implementación. Si tuviera un constructor con parámetros se inyectarían como
		// dependencias.
		// binder.bind(Sevicio.class, ServicioImpl.class);

		// Servicios de persistencia (definidos en Spring por la necesidad de que Spring gestione
		// las transacciones)
		// binder.bind(ProductoDAO.class, ProductoDAOImpl.class);
	}

	// Servicio que delega en Spring la inicialización de Hibernate, solo obtiene la configuración
	// de Hibernate creada por Spring
	public static HibernateSessionSource buildAppHibernateSessionSource(ApplicationContext context) {
		return new HibernateSessionSourceImpl(context);
	}

	public static void contributeServiceOverride(MappedConfiguration<Class, Object> configuration, @Local HibernateSessionSource hibernateSessionSource) {
		configuration.add(HibernateSessionSource.class, hibernateSessionSource);
		// Servicio para usar un CDN lazy, pe. con Amazon CloudFront
		//configuration.addInstance(AssetPathConverter.class, CDNAssetPathConverterImpl.class);

		if (isServidorJBoss(ContextListener.SERVLET_CONTEXT)) {
			configuration.add(ClasspathURLConverter.class, new WildFlyClasspathURLConverter());
		}
	}

	public static void contributeApplicationDefaults(MappedConfiguration<String, Object> configuration) {
		configuration.add(SymbolConstants.HMAC_PASSPHRASE, UUID.randomUUID().toString());

		configuration.add(SymbolConstants.PRODUCTION_MODE, false);
		configuration.add(SymbolConstants.SUPPORTED_LOCALES, "es,en");

		configuration.add(SecuritySymbols.LOGIN_URL, "/login");
		configuration.add(SecuritySymbols.SUCCESS_URL, "/index");
		configuration.add(SecuritySymbols.UNAUTHORIZED_URL, "/unauthorized");
		configuration.add(SecuritySymbols.REDIRECT_TO_SAVED_URL, "true");
		
		configuration.add(SymbolConstants.SECURE_ENABLED, true);
		configuration.add(SymbolConstants.HOSTPORT, 8080);
		configuration.add(SymbolConstants.HOSTPORT_SECURE, 8443);
		configuration.add(SymbolConstants.ENABLE_PAGELOADING_MASK, false);

		configuration.add(SymbolConstants.APPLICATION_VERSION, "1.0");

		configuration.add(SymbolConstants.JAVASCRIPT_INFRASTRUCTURE_PROVIDER, "jquery");

		configuration.add(CDN_DOMAIN_PROTOCOL, "http");
		configuration.add(CDN_DOMAIN_HOST, "s3-eu-west-1.amazonaws.com");
		configuration.add(CDN_DOMAIN_PORT, "");
		configuration.add(CDN_DOMAIN_PATH, "cdn-plugintapestry");
	}

//	public static void contributeSecurityConfiguration(Configuration<SecurityFilterChain> configuration, SecurityFilterChainFactory factory) {
//		configuration.add(factory.createChain("/admin/**").add(factory.authc()).add(factory.ssl()).build());
//	}

	public static void contributeWebSecurityManager(Configuration<Realm> configuration) {
		// Realm básico
//		ExtendedPropertiesRealm realm = new ExtendedPropertiesRealm("classpath:shiro-users.properties");
		
		// Realm con hash criptográfico y «salt»
		Realm realm = new es.com.blogspot.elblogdepicodev.plugintapestry.misc.Realm();

		configuration.add(realm);
	}

	public static void contributeTranslatorSource(MappedConfiguration configuration) {
		configuration.add(Date.class, new DateTranslator("dd/MM/yyyy"));
	}

	public static void contributeModuleManager(MappedConfiguration<String, Object> configuration, @Path("classpath:META-INF/assets/app/jquery-library.js") Resource jQuery) {
		configuration.override("jquery", new JavaScriptModuleConfiguration(jQuery));
	}

	public static void contributeBeanValidatorSource(OrderedConfiguration<BeanValidatorConfigurer> configuration) {
		configuration.add("AppConfigurer", new BeanValidatorConfigurer() {
			public void configure(javax.validation.Configuration<?> configuration) {
				configuration.ignoreXmlConfiguration();
			}
		});
	}

	public static void contributeJavaScriptStackSource(MappedConfiguration<String, JavaScriptStack> configuration) {
		configuration.addInstance("plugin", PlugInStack.class);
	}

	@Contribute(ComponentClassTransformWorker2.class)
	public static void contributeWorkers(OrderedConfiguration<ComponentClassTransformWorker2> configuration) {
		configuration.addInstance("CSRF", CsrfWorker.class);
	}

	private static boolean isServidorJBoss(ServletContext context) {
		if (context == null) {
			return false;
		}
		
		String si = context.getServerInfo();

		if (si.contains("WildFly") || si.contains("JBoss")) {
			return true;
		}

		return false;
	}
}
