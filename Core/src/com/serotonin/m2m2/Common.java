/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.measure.converter.RationalConverter;
import javax.measure.quantity.Quantity;
import javax.measure.unit.Unit;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.async.AsyncLoggerConfig;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.joda.time.Period;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.context.WebApplicationContext;

import com.github.zafarkhaja.semver.Version;
import com.infiniteautomation.mango.CompiledCoreVersion;
import com.infiniteautomation.mango.io.messaging.MessageManager;
import com.infiniteautomation.mango.io.serial.SerialPortManager;
import com.infiniteautomation.mango.monitor.MonitoredValues;
import com.infiniteautomation.mango.spring.MangoRuntimeContextConfiguration;
import com.infiniteautomation.mango.util.LazyInitSupplier;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.pair.StringStringPair;
import com.serotonin.json.JsonContext;
import com.serotonin.m2m2.db.DatabaseProxy;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.module.Module;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.EventManager;
import com.serotonin.m2m2.rt.RuntimeManager;
import com.serotonin.m2m2.rt.maint.BackgroundProcessing;
import com.serotonin.m2m2.rt.maint.work.WorkItem;
import com.serotonin.m2m2.shared.ModuleUtils;
import com.serotonin.m2m2.util.ExportCodes;
import com.serotonin.m2m2.util.license.InstanceLicense;
import com.serotonin.m2m2.util.license.LicenseFeature;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.permission.PermissionHolder.Anonymous;
import com.serotonin.m2m2.vo.permission.PermissionHolder.SystemSuperadmin;
import com.serotonin.m2m2.web.comparators.StringStringPairComparator;
import com.serotonin.provider.Providers;
import com.serotonin.timer.AbstractTimer;
import com.serotonin.timer.CronTimerTrigger;
import com.serotonin.timer.OrderedRealTimeTimer;
import com.serotonin.util.properties.MangoProperties;

import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;

public class Common {
    // Note the start time of the application.
    public static final long START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    public static final MangoProperties envProps = Providers.get(MangoProperties.class);

    /**
     * <p>The Mango Automation installation directory. This is specified by the mango.paths.home system property or the mango_paths_home environment variable.
     * If neither is set the current working directory is used.</p>
     */
    public static final Path MA_HOME_PATH = Paths.get(envProps.getString("paths.home", "")).toAbsolutePath().normalize();

    public static final Path MA_DATA_PATH = createDirectories(MA_HOME_PATH
            .resolve(envProps.getString("paths.data", "")).normalize());

    private static final Path LOGS_PATH = createDirectories(MA_DATA_PATH
            .resolve(envProps.getString("paths.logs", "logs")).normalize());

    public static final Path OVERRIDES = MA_DATA_PATH.resolve(envProps.getString("paths.overrides", Constants.DIR_OVERRIDES));
    public static final Path OVERRIDES_WEB = OVERRIDES.resolve(Constants.DIR_WEB);
    public static final Path WEB = MA_HOME_PATH.resolve(Constants.DIR_WEB);
    public static final Path MODULES = WEB.resolve(Constants.DIR_MODULES);

    private static final Path TEMP_PATH = createDirectories(MA_DATA_PATH.resolve(envProps.getString("paths.temp", System.getProperty("java.io.tmpdir"))).normalize());
    private static final Path FILEDATA_PATH = createDirectories(MA_DATA_PATH.resolve(envProps.getString("paths.filedata", "filedata")).normalize());
    private static final Path BACKUP_PATH = createDirectories(MA_DATA_PATH.resolve(envProps.getString("paths.backup", "backup")).normalize());
    private static final Path MODULE_DATA_PATH = createDirectories(MA_DATA_PATH.resolve(envProps.getString(Module.MODULE_DATA_ENV_PROP, Module.MODULE_DATA_ENV_PROP_DEFAULT)).normalize());
    private static final Path FILE_STORE_PATH = createDirectories(MA_DATA_PATH.resolve(envProps.getString("filestore.location", "filestore")).normalize());

    public static final int NEW_ID = -1;

    public static Properties releaseProps;
    public static Configuration freemarkerConfiguration;
    public static DatabaseProxy databaseProxy;
    public static BackgroundProcessing backgroundProcessing;
    public static EventManager eventManager;
    public static RuntimeManager runtimeManager;
    public static SerialPortManager serialPortManager;
    public static MessageManager messageManager;

    //Used to determine the given size of all Task Queues
    //TODO Remove this and replace with varying size queues
    // depending on the type of task.  This was placed
    // here so we can release 3.0.0 with other features
    // and not have to worry about the various problems/testing
    // for all the different tasks.
    public static int defaultTaskQueueSize = 1;

    public static final String APPLICATION_LOGO = "/images/logo.png";

    public static AbstractTimer timer = new OrderedRealTimeTimer();
    public static final MonitoredValues MONITORED_VALUES = new MonitoredValues();
    public static final JsonContext JSON_CONTEXT = new JsonContext();

    public static final Pattern COMMA_SPLITTER = Pattern.compile("\\s*,\\s*");

    public static final LazyInitSupplier<Integer> LAST_UPGRADE = new LazyInitSupplier<>(() -> {
        return SystemSettingsDao.instance.getIntValue(SystemSettingsDao.LAST_UPGRADE, 0);
    });

    /**
     * @return epoch time in seconds of last upgrade/install of core or modules
     */
    public static int getLastUpgradeTime() {
        try {
            return LAST_UPGRADE.get();
        } catch (Exception e) {
            // possibly due to database not being started
            return 0;
        }
    }

    private static final AtomicReference<ClassLoader> MODULE_CLASS_LOADER_HOLDER = new AtomicReference<>();

    public static void setModuleClassLoader(ClassLoader loader) {
        if (!MODULE_CLASS_LOADER_HOLDER.compareAndSet(null, loader)) {
            throw new IllegalStateException("Module class loader is already set");
        }
    }

    public static ClassLoader getModuleClassLoader() {
        return Objects.requireNonNull(MODULE_CLASS_LOADER_HOLDER.get());
    }

    //
    // License
    static InstanceLicense license;
    static boolean free = true;
    static boolean invalid = false;

    public static InstanceLicense license() {
        return license;
    }

    public static LicenseFeature licenseFeature(String name) {
        if (license != null)
            return license.getFeature(name);
        return null;
    }

    public static boolean isFree() {
        return free;
    }

    public static boolean isInvalid() {
        return invalid;
    }

    public static boolean isCoreSigned() {
        return releaseProps != null && Boolean.TRUE.toString().equals(releaseProps.getProperty("signed"));
    }

    /*
     * Updating the MA version: - Create a DBUpdate subclass for the old version number. This may not do anything in
     * particular to the schema, but is still required to update the system settings so that the database has the
     * correct version.
     */

    public static final Version getVersion() {
        return CoreVersion.INSTANCE.version;
    }

    public static final Version normalizeVersion(Version version) {
        return Version.forIntegers(version.getMajorVersion(), version.getMinorVersion(), version.getPatchVersion());
    }

    public static final int getLicenseAgreementVersion() {
        return CoreVersion.INSTANCE.licenseAgreementVersion;
    }

    private enum CoreVersion {
        INSTANCE();
        final Version version;
        //Track license agreement version to ensure the admin users have accepted the current version of our license
        final int licenseAgreementVersion = 1;

        CoreVersion() {
            Version version = CompiledCoreVersion.VERSION;

            try {
                version = loadCoreVersionFromReleaseProperties(version);
            } catch (Throwable t) {}

            // check for possible license subversion
            if (version.getMajorVersion() != CompiledCoreVersion.VERSION.getMajorVersion()) {
                throw new RuntimeException("Version from release.properties does not match compiled major version " + CompiledCoreVersion.VERSION.getMajorVersion());
            }

            this.version = version;
        }
    }

    private static final Version loadCoreVersionFromReleaseProperties(Version version) {
        if (releaseProps != null) {
            String versionStr = releaseProps.getProperty(ModuleUtils.Constants.PROP_VERSION);
            String buildNumberStr = releaseProps.getProperty(ModuleUtils.Constants.BUILD_NUMBER);

            if (versionStr != null) {
                try {
                    version = Version.valueOf(versionStr);
                } catch (com.github.zafarkhaja.semver.ParseException e) { }
            }

            if (buildNumberStr != null) {
                version = version.setBuildMetadata(buildNumberStr);
            }
        }

        return version;
    }

    /**
     *
     * @return
     */
    public static int getDatabaseSchemaVersion() {
        return 38;
    }

    public static File getLogsDir() {
        return LOGS_PATH.toFile();
    }

    public static Path getLogsPath() {
        return LOGS_PATH;
    }

    public interface TimePeriods {
        int MILLISECONDS = 8;
        int SECONDS = 1;
        int MINUTES = 2;
        int HOURS = 3;
        int DAYS = 4;
        int WEEKS = 5;
        int MONTHS = 6;
        int YEARS = 7;
    }

    public static ExportCodes TIME_PERIOD_CODES = new ExportCodes();
    static {
        TIME_PERIOD_CODES.addElement(TimePeriods.MILLISECONDS, "MILLISECONDS");
        TIME_PERIOD_CODES.addElement(TimePeriods.SECONDS, "SECONDS");
        TIME_PERIOD_CODES.addElement(TimePeriods.MINUTES, "MINUTES");
        TIME_PERIOD_CODES.addElement(TimePeriods.HOURS, "HOURS");
        TIME_PERIOD_CODES.addElement(TimePeriods.DAYS, "DAYS");
        TIME_PERIOD_CODES.addElement(TimePeriods.WEEKS, "WEEKS");
        TIME_PERIOD_CODES.addElement(TimePeriods.MONTHS, "MONTHS");
        TIME_PERIOD_CODES.addElement(TimePeriods.YEARS, "YEARS");
    }

    //pointProperties.jsp probably depends on this and ROLLUP_CODES maintaining this ordering
    // meaning strictly ascending and the same add order to the rollup codes
    public interface Rollups {
        int NONE = 0;
        int AVERAGE = 1;
        int DELTA = 2;
        int MINIMUM = 3;
        int MAXIMUM = 4;
        int ACCUMULATOR = 5;
        int SUM = 6;
        int FIRST = 7;
        int LAST = 8;
        int COUNT = 9;
        int INTEGRAL = 10;
        int ALL = 11;
        int START = 12;
    }
    public static ExportCodes ROLLUP_CODES = new ExportCodes();
    static{
        ROLLUP_CODES.addElement(Rollups.NONE, "NONE", "common.rollup.none");
        ROLLUP_CODES.addElement(Rollups.AVERAGE, "AVERAGE", "common.rollup.average");
        ROLLUP_CODES.addElement(Rollups.DELTA, "DELTA", "common.rollup.delta");
        ROLLUP_CODES.addElement(Rollups.MINIMUM, "MINIMUM", "common.rollup.minimum");
        ROLLUP_CODES.addElement(Rollups.MAXIMUM, "MAXIMUM", "common.rollup.maximum");
        ROLLUP_CODES.addElement(Rollups.ACCUMULATOR, "ACCUMULATOR", "common.rollup.accumulator");
        ROLLUP_CODES.addElement(Rollups.SUM, "SUM", "common.rollup.sum");
        ROLLUP_CODES.addElement(Rollups.FIRST, "FIRST", "common.rollup.first");
        ROLLUP_CODES.addElement(Rollups.LAST, "LAST", "common.rollup.last");
        ROLLUP_CODES.addElement(Rollups.COUNT, "COUNT", "common.rollup.count");
        ROLLUP_CODES.addElement(Rollups.INTEGRAL, "INTEGRAL", "common.rollup.integral");
        ROLLUP_CODES.addElement(Rollups.ALL, "ALL", "common.rollup.all");
        ROLLUP_CODES.addElement(Rollups.START, "START", "common.rollup.start");
    }

    public static ExportCodes WORK_ITEM_CODES = new ExportCodes();
    static {
        WORK_ITEM_CODES.addElement(WorkItem.PRIORITY_HIGH, "PRIORITY_HIGH");
        WORK_ITEM_CODES.addElement(WorkItem.PRIORITY_MEDIUM, "PRIORITY_MEDIUM");
        WORK_ITEM_CODES.addElement(WorkItem.PRIORITY_LOW, "PRIORITY_LOW");
    }

    public static ExportCodes VERSION_STATE_CODES = new ExportCodes();
    static{
        VERSION_STATE_CODES.addElement(UpgradeVersionState.DEVELOPMENT, "DEVELOPMENT", "systemSettings.upgradeState.development");
        VERSION_STATE_CODES.addElement(UpgradeVersionState.ALPHA, "ALPHA", "systemSettings.upgradeState.alpha");
        VERSION_STATE_CODES.addElement(UpgradeVersionState.BETA, "BETA", "systemSettings.upgradeState.beta");
        VERSION_STATE_CODES.addElement(UpgradeVersionState.RELEASE_CANDIDATE, "RELEASE_CANDIDATE", "systemSettings.upgradeState.releaseCanditate");
        VERSION_STATE_CODES.addElement(UpgradeVersionState.PRODUCTION, "PRODUCTION", "systemSettings.upgradeState.production");
    }

    /**
     * Returns the length of time in milliseconds
     */
    public static long getMillis(int periodType, int periods) {
        return getPeriod(periodType, periods).toDurationFrom(null).getMillis();
    }

    public static Period getPeriod(int periodType, int periods) {
        switch (periodType) {
            case TimePeriods.MILLISECONDS:
                return Period.millis(periods);
            case TimePeriods.SECONDS:
                return Period.seconds(periods);
            case TimePeriods.MINUTES:
                return Period.minutes(periods);
            case TimePeriods.HOURS:
                return Period.hours(periods);
            case TimePeriods.DAYS:
                return Period.days(periods);
            case TimePeriods.WEEKS:
                return Period.weeks(periods);
            case TimePeriods.MONTHS:
                return Period.months(periods);
            case TimePeriods.YEARS:
                return Period.years(periods);
            default:
                throw new ShouldNeverHappenException("Unsupported time period: " + periodType);
        }
    }

    public static TranslatableMessage getPeriodDescription(int periodType, int periods) {
        String periodKey;
        switch (periodType) {
            case TimePeriods.MILLISECONDS:
                periodKey = "common.tp.milliseconds";
                break;
            case TimePeriods.SECONDS:
                periodKey = "common.tp.seconds";
                break;
            case TimePeriods.MINUTES:
                periodKey = "common.tp.minutes";
                break;
            case TimePeriods.HOURS:
                periodKey = "common.tp.hours";
                break;
            case TimePeriods.DAYS:
                periodKey = "common.tp.days";
                break;
            case TimePeriods.WEEKS:
                periodKey = "common.tp.weeks";
                break;
            case TimePeriods.MONTHS:
                periodKey = "common.tp.months";
                break;
            case TimePeriods.YEARS:
                periodKey = "common.tp.years";
                break;
            default:
                throw new ShouldNeverHappenException("Unsupported time period: " + periodType);
        }

        return new TranslatableMessage("common.tp.description", periods, new TranslatableMessage(periodKey));
    }

    /**
     * <p>Get the current principal from the Spring Security context (stored in a ThreadLocal).
     * If you require an instance of {@link User} you should call {@link PermissionHolder#getUser()}
     * which may be null.</p>
     *
     * <p>Note that this method will sometimes return {@link SystemSuperadmin} or {@link Anonymous} which
     * are not associated with a particular user.</p>
     *
     * @return the current security principal (never null)
     * @throws PermissionException if there is no authentication, or the principal is not a {@link PermissionHolder}
     */
    @NonNull
    public static PermissionHolder getUser() throws PermissionException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Object principle = auth.getPrincipal();
            // Ensure that the principle is a permission holder
            if (principle instanceof PermissionHolder) {
                return (PermissionHolder) principle;
            } else {
                throw new PermissionException(new TranslatableMessage("permission.exception.invalidAuthenticationPrinciple"), null);
            }
        }
        throw new PermissionException(new TranslatableMessage("permission.exception.noAuthenticationSet"), null);
    }

    public static Path getFiledataPath() {
        return FILEDATA_PATH;
    }

    public static Path getTempPath() {
        return TEMP_PATH;
    }

    public static Path getBackupPath() {
        return BACKUP_PATH;
    }

    public static Path getModuleDataPath() {
        return MODULE_DATA_PATH;
    }

    public static Path getFileStorePath() {
        return FILE_STORE_PATH;
    }

    public static CronTimerTrigger getCronTrigger(int periodType, int delaySeconds) {
        int delayMinutes = 0;
        if (delaySeconds >= 60) {
            delayMinutes = delaySeconds / 60;
            delaySeconds %= 60;

            if (delayMinutes >= 60)
                delayMinutes = 59;
        }

        try {
            switch (periodType) {
                case TimePeriods.MILLISECONDS:
                    throw new ShouldNeverHappenException("Can't create a cron trigger for milliseconds");
                case TimePeriods.SECONDS:
                    return new CronTimerTrigger("* * * * * ?");
                case TimePeriods.MINUTES:
                    return new CronTimerTrigger(delaySeconds + " * * * * ?");
                case TimePeriods.HOURS:
                    return new CronTimerTrigger(delaySeconds + " " + delayMinutes + " * * * ?");
                case TimePeriods.DAYS:
                    return new CronTimerTrigger(delaySeconds + " " + delayMinutes + " 0 * * ?");
                case TimePeriods.WEEKS:
                    return new CronTimerTrigger(delaySeconds + " " + delayMinutes + " 0 ? * MON");
                case TimePeriods.MONTHS:
                    return new CronTimerTrigger(delaySeconds + " " + delayMinutes + " 0 1 * ?");
                case TimePeriods.YEARS:
                    return new CronTimerTrigger(delaySeconds + " " + delayMinutes + " 0 1 JAN ?");
                default:
                    throw new ShouldNeverHappenException("Invalid cron period type: " + periodType);
            }
        }
        catch (ParseException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    //
    // Misc

    /**
     * Uses BCrypt by default
     * @param plaintext
     * @return
     */
    public static String encrypt(String plaintext) {
        String alg = getHashAlgorithm();
        String hash = encrypt(plaintext, alg);
        return "{" + alg + "}" + hash;
    }

    public static String getHashAlgorithm() {
        return envProps.getString("security.hashAlgorithm", User.BCRYPT_ALGORITHM);
    }

    public static String encrypt(String plaintext, String alg) {
        try {
            if (User.NONE_ALGORITHM.equals(alg))
                return plaintext;

            if (User.BCRYPT_ALGORITHM.equals(alg)) {
                int log2Rounds = envProps.getInt("security.bcrypt.log2Rounds", 10);
                return BCrypt.hashpw(plaintext, BCrypt.gensalt(log2Rounds));
            }

            MessageDigest md = MessageDigest.getInstance(alg);
            if (md == null)
                throw new ShouldNeverHappenException("MessageDigest algorithm " + alg
                        + " not found. Set the 'security.hashAlgorithm' property in env.properties appropriately. "
                        + "Use 'NONE' for no hashing.");
            md.update(plaintext.getBytes(StandardCharsets.UTF_8));
            byte raw[] = md.digest();
            String hash = new String(Base64.encodeBase64(raw));
            return hash;
        }
        catch (NoSuchAlgorithmException e) {
            // Should never happen, so just wrap in a runtime exception and rethrow
            throw new ShouldNeverHappenException(e);
        }
    }

    public static boolean verifyProperties(InputStream in, boolean signed, Map<String, String> properties) {
        return Providers.get(IMangoLifecycle.class).verifyProperties(in, signed, properties);
    }

    public static boolean checkPassword(String plaintext, String hashed) {
        return checkPassword(plaintext, hashed, false);
    }

    public static final Pattern EXTRACT_ALGORITHM_HASH = Pattern.compile("^\\{(.+?)\\}(.*)");

    public static boolean checkPassword(String password, String storedHash, boolean passwordEncrypted) {
        try {
            if (password == null || storedHash == null)
                return false;

            if (passwordEncrypted) {
                return storedHash.equals(password);
            }

            Matcher m = EXTRACT_ALGORITHM_HASH.matcher(storedHash);
            if (!m.matches()) {
                return false;
            }
            String algorithm = m.group(1);
            String hash = m.group(2);

            if (User.BCRYPT_ALGORITHM.equals(algorithm)) {
                return BCrypt.checkpw(password, hash);
            } else if (User.NONE_ALGORITHM.equals(algorithm)) {
                return hash.equals(password);
            } else if (User.LOCKED_ALGORITHM.equals(algorithm)) {
                return false;
            } else {
                return hash.equals(encrypt(password, algorithm));
            }

        } catch (Throwable t) {
            return false;
        }
    }

    public static String extractHashAlgorithm(String hash) {
        Matcher m = EXTRACT_ALGORITHM_HASH.matcher(hash);
        if (!m.matches()) {
            return null;
        }
        return m.group(1);
    }

    //
    // HttpClient

    /**
     * Get default HTTP Client with 30s timeout
     * @return
     */
    public static HttpClient getHttpClient() {
        return getHttpClient(30000); // 30 seconds.
    }

    /**
     * Get HTPT Client with default settings and assigned timeout and retries
     * @param timeout
     * @param retries
     * @return
     */
    public static HttpClient getHttpClient(int timeout, int retries) {
        // Create global request configuration
        Builder defaultRequestConfigBuilder = getDefaultRequestConfig();
        defaultRequestConfigBuilder.setSocketTimeout(timeout)
        .setConnectTimeout(timeout);

        HttpClientBuilder builder = getDefaultHttpClientBuilder();
        builder.setRetryHandler(new DefaultHttpRequestRetryHandler(retries, false));
        builder.setDefaultRequestConfig(defaultRequestConfigBuilder.build());
        return builder.build();
    }

    /**
     * Get an HTTP Client with default settings and assigned timeout
     * @param timeout
     * @return
     */
    public static HttpClient getHttpClient(int timeout) {
        // Create global request configuration
        Builder defaultRequestConfigBuilder = getDefaultRequestConfig();
        defaultRequestConfigBuilder.setSocketTimeout(timeout).setConnectTimeout(timeout).build();

        HttpClientBuilder builder = getDefaultHttpClientBuilder();
        builder.setDefaultRequestConfig(defaultRequestConfigBuilder.build());
        return builder.build();
    }

    /**
     * Get a builder defaulted with System Settings values
     * @return
     */
    public static HttpClientBuilder getDefaultHttpClientBuilder() {
        if (SystemSettingsDao.instance.getBooleanValue(SystemSettingsDao.HTTP_CLIENT_USE_PROXY)) {
            String proxyHost = SystemSettingsDao.instance.getValue(SystemSettingsDao.HTTP_CLIENT_PROXY_SERVER);
            int proxyPort = SystemSettingsDao.instance.getIntValue(SystemSettingsDao.HTTP_CLIENT_PROXY_PORT);
            String username = SystemSettingsDao.instance.getValue(SystemSettingsDao.HTTP_CLIENT_PROXY_USERNAME);
            String password = SystemSettingsDao.instance.getValue(SystemSettingsDao.HTTP_CLIENT_PROXY_PASSWORD);

            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), new UsernamePasswordCredentials(
                    username, password));
            // Add the given custom dependencies and configuration.
            return HttpClients.custom()
                    .setProxy(new HttpHost(proxyHost, proxyPort))
                    .setDefaultCredentialsProvider(credentialsProvider);
        }
        else {
            return HttpClients.custom();
        }
    }

    /**
     * Get the default request config builder to customize
     * @return
     */
    public static Builder getDefaultRequestConfig() {
        return RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT)
                .setExpectContinueEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST))
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC));

    }

    //
    //
    // i18n
    //
    private static Object i18nLock = new Object();
    private static String systemLanguage;
    private static Translations systemTranslations;
    private static Locale systemLocale;

    private static final LazyInitSupplier<List<StringStringPair>> LANGUAGES = new LazyInitSupplier<>(() -> {
        List<StringStringPair> languages = new ArrayList<>();
        for (String localeStr : ModuleRegistry.getLocales()) {
            Locale locale = parseLocale(localeStr);
            if (locale != null) {
                languages.add(new StringStringPair(localeStr, Translations.getTranslations(locale).translate("locale.name")));
            }
        }
        StringStringPairComparator.sort(languages);
        return languages;
    });

    public static String translate(String key) {
        ensureI18n();
        return systemTranslations.translate(key);
    }

    public static Translations getTranslations() {
        ensureI18n();
        return systemTranslations;
    }

    public static Locale getLocale() {
        ensureI18n();
        return systemLocale;
    }

    private static void ensureI18n() {
        if (systemLanguage == null) {
            synchronized (i18nLock) {
                if (systemLanguage == null) {
                    systemLanguage = SystemSettingsDao.instance.getValue(SystemSettingsDao.LANGUAGE);
                    systemLocale = parseLocale(systemLanguage);
                    if (systemLocale == null)
                        throw new IllegalArgumentException("Locale for given language not found: " + systemLanguage);
                    systemTranslations = Translations.getTranslations(systemLocale);
                }
            }
        }
    }

    public static String getMessage(String key, Object... args) {
        String pattern = translate(key);
        return MessageFormat.format(pattern, args);
    }

    public static void setSystemLanguage(String language) {
        if (parseLocale(language) == null)
            throw new IllegalArgumentException("Locale for given language not found: " + language);
        systemLanguage = null;
        systemLocale = null;
        systemTranslations = null;
    }

    public static List<StringStringPair> getLanguages() {
        return LANGUAGES.get();
    }

    public static Locale parseLocale(String str) {
        String[] parts = str.split("_");
        Locale locale = null;
        if (parts.length == 1)
            locale = new Locale(parts[0]);
        else if (parts.length == 2)
            locale = new Locale(parts[0], parts[1]);
        else if (parts.length == 3)
            locale = new Locale(parts[0], parts[1], parts[2]);
        return locale;
    }

    public static String generateXid(String prefix) {
        return prefix + UUID.randomUUID();
    }

    /**
     * Get the HTTP/HTTPS Cookie Name based on scheme and port
     * @return
     */
    public static String getCookieName() {
        if (Common.envProps.getBoolean("sessionCookie.useGuid", true)) {
            return Providers.get(ICoreLicense.class).getGuid();
        }

        String cookieName = Common.envProps.getString("sessionCookie.name");
        if (cookieName != null && !cookieName.isEmpty()) {
            return cookieName;
        }

        if(Common.envProps.getBoolean("ssl.on", false)) {
            return "MANGO" + Common.envProps.getInt("ssl.port", 443);
        }else {
            return "MANGO" + Common.envProps.getInt("web.port", 8080);
        }
    }

    /* Spring application contexts */

    /**
     * Gets the spring root web application context, only set after the context is refreshed (started).
     * If its not null, its safe to use.
     *
     * @return the Spring root web application context if it has been refreshed, otherwise null
     */
    public static WebApplicationContext getRootWebContext() {
        return MangoRuntimeContextConfiguration.getRootWebContext();
    }

    /**
     * Gets the spring runtime application context, only set after the context is refreshed (started).
     * If its not null, its safe to use.
     *
     * @return the Spring runtime application context if it has been refreshed, otherwise null
     */
    public static ApplicationContext getRuntimeContext() {
        return MangoRuntimeContextConfiguration.getRuntimeContext();
    }

    /**
     * Get a bean from the runtime context.
     * @param clazz
     * @return
     */
    public static <T> T getBean(Class<T> clazz) {
        return getRuntimeContext().getBean(clazz);
    }

    /**
     * Get a bean from the runtime context.
     * @param clazz
     * @param name
     * @return
     */
    public static <T> T getBean(Class<T> clazz, String name) {
        return getRuntimeContext().getBean(name, clazz);
    }

    private static Path createDirectories(Path input) {
        try {
            return Files.createDirectories(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copy all files from srcDirectory to dstDirectory. Preserves existing files by default unless
     * {@link java.nio.file.StandardCopyOption#REPLACE_EXISTING REPLACE_EXISTING} is supplied as an option.
     *
     * @param srcDirectory
     * @param dstDirectory
     * @param options
     * @throws IOException
     */
    public static void copyDirectory(Path srcDirectory, Path dstDirectory, CopyOption... options) throws IOException {
        boolean replaceExisting = Arrays.stream(options).anyMatch(o -> o == StandardCopyOption.REPLACE_EXISTING);

        try (Stream<Path> srcFiles = Files.walk(srcDirectory)) {
            for (Path srcFile : (Iterable<Path>) srcFiles::iterator) {
                Path relativePath = srcDirectory.relativize(srcFile);
                Path dstFile = dstDirectory.resolve(relativePath);

                if (Files.isDirectory(srcFile)) {
                    Files.createDirectories(dstFile);
                } else if (Files.isRegularFile(srcFile)) {
                    if (replaceExisting || !Files.exists(dstFile)) {
                        Files.copy(srcFile, dstFile, options);
                    }
                }
            }
        }
    }

    /**
     * Tokenizes a string, splitting on the pattern but keeping the delimiters.
     *
     * @param pattern
     * @param input
     * @return
     */
    public static List<String> tokenize(Pattern pattern, String input) {
        List<String> tokens = new ArrayList<>();

        Matcher matcher = pattern.matcher(input);
        int position = 0;
        while (matcher.find()) {
            String prevToken = input.substring(position, matcher.start());
            if (!prevToken.isEmpty()) {
                tokens.add(prevToken);
            }
            position = matcher.end();
            tokens.add(matcher.group());
        }

        String lastToken = input.substring(position);
        if (!lastToken.isEmpty()) {
            tokens.add(lastToken);
        }

        return tokens;
    }

    private static final RationalConverter EXBI_CONVERTER = new RationalConverter(1152921504606846976L,1);
    private static final RationalConverter PEBI_CONVERTER = new RationalConverter(1125899906842624L,1);
    private static final RationalConverter TEBI_CONVERTER = new RationalConverter(1099511627776L,1);
    private static final RationalConverter GIBI_CONVERTER = new RationalConverter(1073741824L, 1);
    private static final RationalConverter MEBI_CONVERTER = new RationalConverter(1048576L, 1);
    private static final RationalConverter KIBI_CONVERTER = new RationalConverter(1024L, 1);

    public static <Q extends Quantity> Unit<Q> EXBI(Unit<Q> unit) {
        return unit.transform(EXBI_CONVERTER);
    }
    public static <Q extends Quantity> Unit<Q> PEBI(Unit<Q> unit) {
        return unit.transform(PEBI_CONVERTER);
    }
    public static <Q extends Quantity> Unit<Q> TEBI(Unit<Q> unit) {
        return unit.transform(TEBI_CONVERTER);
    }
    public static <Q extends Quantity> Unit<Q> GIBI(Unit<Q> unit) {
        return unit.transform(GIBI_CONVERTER);
    }
    public static <Q extends Quantity> Unit<Q> MEBI(Unit<Q> unit) {
        return unit.transform(MEBI_CONVERTER);
    }
    public static <Q extends Quantity> Unit<Q> KIBI(Unit<Q> unit) {
        return unit.transform(KIBI_CONVERTER);
    }

    /**
     * Recursively finds all Freemarker template files in configured loader paths. Only supports FileTemplateLoader.
     * @return Set of paths to the Freemarker templates, relative to the Freemarker loader base directories
     */
    public static Set<Path> getFreemarkerTemplates() {
        Set<Path> templatePaths = new HashSet<>();

        TemplateLoader loader = Common.freemarkerConfiguration.getTemplateLoader();
        if (loader instanceof MultiTemplateLoader) {
            MultiTemplateLoader multiTemplateLoader = (MultiTemplateLoader) loader;
            for (int i = 0; i < multiTemplateLoader.getTemplateLoaderCount(); i++) {
                TemplateLoader childLoader = multiTemplateLoader.getTemplateLoader(i);
                if (childLoader instanceof FileTemplateLoader) {
                    FileTemplateLoader fileTemplateLoader = (FileTemplateLoader) childLoader;
                    Path baseDir = fileTemplateLoader.getBaseDirectory().toPath();
                    if (Files.isDirectory(baseDir)) {
                        listRecursive(baseDir)
                                .filter(p -> p.getFileName().toString().endsWith(".ftl"))
                                .map(baseDir::relativize)
                                .forEach(templatePaths::add);
                    }
                }
            }
        }

        return templatePaths;
    }

    /**
     * Recursively lists all files in a directory
     * @param baseDir
     * @return
     */
    public static Stream<Path> listRecursive(Path baseDir) {
        try {
            return Files.isDirectory(baseDir) ? Files.list(baseDir).flatMap(Common::listRecursive) : Stream.of(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void configureLoggerLevel(String loggerName, Level logLevel) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        LoggerConfig rootLogger = config.getRootLogger();
        LoggerConfig loggerConfig;
        if (rootLogger instanceof AsyncLoggerConfig) {
            loggerConfig = AsyncLoggerConfig.createLogger(true, logLevel, loggerName,
                    Boolean.toString(rootLogger.isIncludeLocation()),
                    new AppenderRef[0], null, config, null);
        } else {
            loggerConfig = LoggerConfig.createLogger(true, logLevel, loggerName,
                    Boolean.toString(rootLogger.isIncludeLocation()),
                    new AppenderRef[0], null, config, null);
        }
        config.addLogger(loggerName, loggerConfig);
        context.updateLoggers();
    }

    public static void removeLoggerConfig(String loggerName) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        config.removeLogger(loggerName);
        context.updateLoggers();
    }
}
