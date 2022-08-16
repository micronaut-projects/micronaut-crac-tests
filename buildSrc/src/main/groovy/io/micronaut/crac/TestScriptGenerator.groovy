package io.micronaut.crac

import groovy.transform.CompileStatic
import io.micronaut.starter.options.BuildTool

import static CracProjectGenerator.DEFAULT_APP_NAME
import static io.micronaut.starter.options.BuildTool.GRADLE
import static io.micronaut.starter.options.BuildTool.MAVEN

@CompileStatic
class TestScriptGenerator {

    public static final String GITHUB_WORKFLOW_JAVA_CI = 'Java CI'
    public static final String ENV_GITHUB_WORKFLOW = 'GITHUB_WORKFLOW'

    private static List<String> guidesChanged(List<String> changedFiles) {
        changedFiles.findAll { path ->
            path.startsWith('guides')
        }.collect { path ->
            String guideFolder = path.substring('guides/'.length())
            guideFolder.substring(0, guideFolder.indexOf('/'))
        }.unique()
    }

    private static boolean changesMicronautVersion(List<String> changedFiles) {
        changedFiles.any { it.contains("version.txt") }
    }

    private static boolean changesDependencies(List<String> changedFiles, List<String> changedGuides) {
        if (changedGuides) {
            return false
        }
        changedFiles.any { it.contains("pom.xml") }
    }

    private static boolean changesBuildScr(List<String> changedFiles) {
        changedFiles.any { it.contains('buildSrc') }
    }

    private static boolean shouldSkip(CracMetadata metadata,
                                      List<String> guidesChanged,
                                      boolean forceExecuteEveryTest) {

        if (metadata.skip) {
            return true
        }

        if (forceExecuteEveryTest) {
            return false
        }

        return !guidesChanged.contains(metadata.slug)
    }

    static String generateScript(File guidesFolder,
                                 String metadataConfigName,
                                 List<String> changedFiles) {
        List<String> slugsChanged = guidesChanged(changedFiles)
        boolean forceExecuteEveryTest = changesMicronautVersion(changedFiles) ||
                changesDependencies(changedFiles, slugsChanged) ||
                changesBuildScr(changedFiles) ||
                (System.getenv(ENV_GITHUB_WORKFLOW) && System.getenv(ENV_GITHUB_WORKFLOW) != GITHUB_WORKFLOW_JAVA_CI) ||
                (!changedFiles && !System.getenv(ENV_GITHUB_WORKFLOW))

        List<CracMetadata> metadatas = CracProjectGenerator.parseAllMetadata(guidesFolder, metadataConfigName)
        metadatas = metadatas.findAll { !shouldSkip(it, slugsChanged, forceExecuteEveryTest) }
        generateScript(metadatas)
    }

    static void generateTestScript(File output,
                                   List<CracMetadata> metadatas) {
        String script = generateScript(metadatas)
        generateTestScript(output, script)
    }

    static void generateTestScript(File output, String script, String scriptFileName = "test.sh") {
        File testScript = new File(output, scriptFileName)
        testScript.createNewFile()
        testScript.text = script
        testScript.executable = true
    }

    static String generateScript(List<CracMetadata> metadatas) {
        def i = TestScriptGenerator.getResourceAsStream("/script-preamble.sh")
        String collect = new BufferedReader(new InputStreamReader(i)).text
        StringBuilder bashScript = new StringBuilder(collect)
        metadatas.sort { it.slug }
        for (CracMetadata metadata : metadatas) {
            List<CracOption> guidesOptionList = CracProjectGenerator.guidesOptions(metadata)
            bashScript.append("\n")
            for (CracOption guidesOption : guidesOptionList) {
                String folder = CracProjectGenerator.folderName(metadata.slug, guidesOption)
                BuildTool buildTool = folder.contains(MAVEN.toString()) ? MAVEN : GRADLE
                if (metadata.apps.any { it.name == DEFAULT_APP_NAME } ) {
                    bashScript << scriptForFolder(folder, folder, buildTool)
                } else {
                    bashScript << """\
cd $folder
"""
                    for (CracMetadata.App app : metadata.apps) {
                        bashScript << scriptForFolder(app.name, folder + '/' + app.name, buildTool)
                    }
                    bashScript << """\
cd ..
"""
                }
            }
        }

        bashScript << '''
if [ ${#FAILED_PROJECTS[@]} -ne 0 ]; then
  echo ""
  echo "-------------------------------------------------"
  echo "Projects with errors:"
  for p in `echo ${FAILED_PROJECTS[@]}`; do
    echo "  $p"
  done;
  echo "-------------------------------------------------"
  exit 1
else
  exit 0
fi
'''

        bashScript
    }

    private static String scriptForFolder(String nestedFolder, String folder, BuildTool buildTool) {
        String bashScript = """\
cd $nestedFolder
echo "-------------------------------------------------"
echo "Building '$folder'"
${buildTool == MAVEN ? './mvnw clean package' : './gradlew assemble' } || EXIT_STATUS=\$?
result="\$(testcheckpoint ${buildTool == MAVEN ? 'target/micronautguide-0.1.jar' : 'build/libs/micronautguide-0.1-all.jar' })
exitCode=\$?
echo "Test checkpoint exit code: \$exitCode (\$result)"
cd ..
"""
        bashScript += """\
if [ \$EXIT_STATUS -ne 0 ]; then
  FAILED_PROJECTS=("\${FAILED_PROJECTS[@]}" $folder)
  echo "'$folder' tests failed => exit \$EXIT_STATUS"
fi
EXIT_STATUS=0


"""
        bashScript
    }
}
