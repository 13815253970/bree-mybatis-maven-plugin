package cn.ttzero.plugin.bree.mybatis;

import java.io.File;
import java.io.IOException;

import cn.ttzero.plugin.bree.mybatis.dataloaders.BreeTableLoader;
import cn.ttzero.plugin.bree.mybatis.utils.CmdUtil;
import cn.ttzero.plugin.bree.mybatis.utils.ConfigUtil;
import cn.ttzero.plugin.bree.mybatis.utils.ConfInit;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import cn.ttzero.plugin.bree.mybatis.dataloaders.BreeLoader;

import fmpp.ProcessingException;
import fmpp.progresslisteners.ConsoleProgressListener;
import fmpp.setting.SettingException;
import fmpp.setting.Settings;
import fmpp.tdd.EvalException;
import fmpp.tdd.Interpreter;
import fmpp.util.MiscUtil;

/**
 * bree-mybatis 代码生成器
 * Created by guanquan.wang at 2019-05-24 09:02
 *
 * @goal gen
 * @phase generate -sources
 */
public class BreeMojo extends AbstractMojo {

    /**
     * The constant cmdUtil.
     */
    private CmdUtil cmdUtil = new CmdUtil();

    /**
     * Location of the output files.
     *
     * @parameter default-value="./src/"
     */
    private File outputDirectory;

    /**
     * Location of the FreeMarker template files.
     * ${project.build.directory}/generated-sources/fmpp/
     *
     * @parameter default-value="./bree/templates/"
     * @since 1.0
     */
    private File templateDirectory;

    /**
     * 配置文件
     *
     * @parameter default-value="./bree/config/config.xml"
     */
    private File config;

    /**
     * copyTemplate
     *
     * @parameter default-value=true
     */
    private boolean copyTemplate;

    private boolean testF = false;

    /**
     * Instantiates a new Bree mojo.
     */
    public BreeMojo() {
        super();
    }

    /**
     * Instantiates a new Bree mojo. for Test
     *
     * @param outputDirectory   the output directory
     * @param templateDirectory the template directory
     * @param config            the config
     */
    public BreeMojo(File outputDirectory, File templateDirectory, File config, boolean testF) {
        this.outputDirectory = outputDirectory;
        this.templateDirectory = templateDirectory;
        this.config = config;
        this.testF = testF;
    }

    /**
     * Execute.
     *
     * @throws MojoExecutionException the mojo execution exception
     * @throws MojoFailureException   the mojo failure exception
     */
    public void execute() throws MojoExecutionException, MojoFailureException {

        configInit(testF);

        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                getLog().error("创建输出目录[" + outputDirectory + "]失败.");
                return;
            }
        }
        getLog().info("创建输出目录成功." + outputDirectory);

        try {
            ConfigUtil.readConfig(config);
            String _cmd = cmdUtil.consoleInput();
            if ("q".equals(_cmd)) {
                getLog().info("Bye!");
                return;
            }
            ConfigUtil.breePath = config.getParentFile().getParent();
            executeInit();
            executeGen();
        } catch (Exception e) {
            getLog().error(MiscUtil.causeMessages(e));
            throw new MojoFailureException(MiscUtil.causeMessages(e), e);
        }
    }

    /**
     * Config init.
     *
     * @throws MojoExecutionException the mojo execution exception
     */
    private void configInit(boolean testF) throws MojoExecutionException {
        if (testF) {
            return;
        }

        // Copy template if need
        if (this.copyTemplate) {
            getLog().info("初始化配置信息开始");
            ConfInit.configInit(this);
            getLog().info("初始化配置信息开始结束 - 请在[" + config + "]中配置数据源");
        }

    }

    /**
     * Execute init.
     *
     * @throws SettingException    the setting exception
     * @throws IOException         the io exception
     * @throws EvalException       the eval exception
     * @throws ProcessingException the processing exception
     */
    private void executeInit() throws SettingException, IOException, EvalException,
        ProcessingException {
        Settings settings = new Settings(new File("."));
        settings.set(Settings.NAME_SOURCE_ROOT, templateDirectory.getAbsolutePath());
        settings.set(Settings.NAME_OUTPUT_ROOT, config.getParentFile().getParent());
        settings.set(Settings.NAME_OUTPUT_ENCODING, "UTF-8");
        settings.set(Settings.NAME_SOURCE_ENCODING, "UTF-8");

        // The table loader
        settings.set(Settings.NAME_DATA, "bree: " + BreeTableLoader.class.getName()
            + "(),project:1");
        settings.set(Settings.NAME_MODES, Interpreter.evalAsSequence("ignore(*/config/*.*),"
            + "ignore(lib/*.*)," + "ignore(css/*.*),ignore(bree/*.*)"));

        settings.addProgressListener(new ConsoleProgressListener());
        settings.execute();

        getLog().info("初始化表完成");
    }

    /**
     * Execute gen.
     *
     * @throws SettingException    the setting exception
     * @throws EvalException       the eval exception
     * @throws ProcessingException the processing exception
     */
    private void executeGen() throws SettingException, EvalException, ProcessingException {
        Settings settings = new Settings(new File("."));
        settings.set(Settings.NAME_SOURCE_ROOT, templateDirectory.getAbsolutePath());
        settings.set(Settings.NAME_OUTPUT_ROOT, outputDirectory.getAbsolutePath());
        settings.set(Settings.NAME_OUTPUT_ENCODING, "UTF-8");
        settings.set(Settings.NAME_SOURCE_ENCODING, "UTF-8");

        // The implement loader
        settings.set(Settings.NAME_DATA, "bree: " + BreeLoader.class.getName() + "()");
        settings.set(Settings.NAME_MODES, Interpreter.evalAsSequence("ignore(config/*.*),"
            + "ignore(lib/*.*)," + "ignore(css/*.*),ignore(init/*.*)"));

        settings.addProgressListener(new ConsoleProgressListener());
        settings.execute();

        getLog().info("bree-mybatis成功生成");
    }

    /**
     * Sets cmd util. forTest
     *
     * @param cmdUtil the cmd util
     */
    public void setCmdUtil(CmdUtil cmdUtil) {
        this.cmdUtil = cmdUtil;
    }

    /**
     * Gets output directory.
     *
     * @return the output directory
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Gets template directory.
     *
     * @return the template directory
     */
    public File getTemplateDirectory() {
        return templateDirectory;
    }

    /**
     * Gets config.
     *
     * @return the config
     */
    public File getConfig() {
        return config;
    }

    /**
     * Is copy template boolean.
     *
     * @return the boolean
     */
    public boolean isCopyTemplate() {
        return copyTemplate;
    }
}