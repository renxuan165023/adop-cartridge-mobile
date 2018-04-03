// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"
def mobileFolderName =  projectFolderName+ "/Mobile_Apps"
def mobileFolder = folder(mobileFolderName) { displayName('Mobile Applications') }

// Jobs
def codeanalysis = freeStyleJob(mobileFolderName + "/Code_Analysis")
def buildapplication = freeStyleJob(mobileFolderName + "/Build_Application")
def functionaltest = freeStyleJob(mobileFolderName + "/Functional_Test")
def serverappium = freeStyleJob(mobileFolderName + "/Server_Appium")
def deploy = freeStyleJob(mobileFolderName + "/Deploy")


//Pipeline
def sample_pipeline = buildPipelineView(mobileFolderName + "/Mobile_Apps")

sample_pipeline.with{
	title('Mobile_Applications_Pipeline')
    displayedBuilds(5)
    selectedJob(mobileFolderName + "/Build_Application")
	refreshFrequency(5)
}

// Job Configuration
buildapplication.with{

	parameters{
		stringParam("application_name","","Application name of APK")
		stringParam("git_url","","URL of the git repository that contains the project")
		stringParam("source_name","","Folder name of the codes to build ( e.g, src )")
		stringParam("test_name","","Folder name of the codes for Functional test")
		stringParam("ip","","Public IP of your server")
	}
	scm{
		git{
		  remote{
			url('$git_url')
			credentials("adop-jenkins-master")
		  }
		  branch("*/master")
		}
	}
	label("WindowsSlave")
	steps{
		batchFile('Rem delete old build\ngradlew clean')
		batchFile('Rem build new\ngradlew assembleRelease')
	}
	publishers{
		downstreamParameterized{
		  trigger("Functional_Test,Server_Appium"){
				condition("SUCCESS")
				parameters{
					predefinedProp("CUSTOM_WORKSPACE",'$WORKSPACE')
					predefinedProp("application_name",'$application_name')
					predefinedProp("git_url",'$git_url')
					predefinedProp("source_name",'$source_name')
					predefinedProp("test_name",'$test_name')
					predefinedProp("ip",'$ip')
				}
			}
		}
	}
}

codeanalysis.with{

	parameters{
		stringParam("CUSTOM_WORKSPACE","","")
		stringParam("application_name","","Application name of APK")
		stringParam("git_url","","URL of the git repository that contains the project")
		stringParam("source_name","","Folder name of the codes to build ( e.g, src )")
		stringParam("test_name","","Folder name of the codes for Functional test")
		stringParam("ip","","Public IP of your server")
	}
	scm{
		git{
		  remote{
			url('$git_url')
			credentials("adop-jenkins-master")
		  }
		  branch("*/master")
		}
	}
	configure { Project -> Project / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'{
            project('')
            properties('''# Required metadata
sonar.projectKey=MobileApp
sonar.projectName=Code_Analysis
sonar.projectVersion=1.0
sonar.sources=$source_name''')

            javaOpts('')
            additionalArguments('')
            jdk('(Inherit From Job)')
            task('')
        }
	}
	publishers{
		downstreamParameterized{
		  trigger("Build_Application"){
				condition("SUCCESS")
				parameters{
					predefinedProp("CUSTOM_WORKSPACE",'$CUSTOM_WORKSPACE')
					predefinedProp("application_name",'$application_name')
					predefinedProp("git_url",'$git_url')
					predefinedProp("source_name",'$source_name')
					predefinedProp("test_name",'$test_name')
					predefinedProp("ip",'$ip')
				}
			}
		}
	}
}

functionaltest.with{

	parameters{
		stringParam("CUSTOM_WORKSPACE","","")
		stringParam("application_name","","Application name of APK")
		stringParam("git_url","","URL of the git repository that contains the project")
		stringParam("source_name","","Folder name of the codes to build ( e.g, src )")
		stringParam("test_name","","Folder name of the codes for Functional test")
		stringParam("ip","","Public IP of your server")
	}
	label("WindowsSlave")
	quietPeriod(30)
	scm{
		git{
		  remote{
			url('$git_url')
			credentials("adop-jenkins-master")
		  }
		  branch("*/master")
		}
	}
	steps{
		shell('cd $test_name\nmvn test')
	}
	
	publishers{
		downstreamParameterized{
		  trigger("Deploy"){
				condition("SUCCESS")
				parameters{
					predefinedProp("CUSTOM_WORKSPACE",'$CUSTOM_WORKSPACE')
					predefinedProp("application_name",'$application_name')
					predefinedProp("git_url",'$git_url')
					predefinedProp("source_name",'$source_name')
					predefinedProp("test_name",'$test_name')
					predefinedProp("ip",'$ip')
				}
			}
		}
	}
}

serverappium.with{

	parameters{
		stringParam("CUSTOM_WORKSPACE","","")
		stringParam("application_name","","Application name of APK")
		stringParam("git_url","","URL of the git repository that contains the project")
		stringParam("source_name","","Folder name of the codes to build ( e.g, src )")
		stringParam("test_name","","Folder name of the codes for Functional test")
		stringParam("ip","","Public IP of your server")
	}
	label("WindowsSlave")
	steps{
		batchFile("cd C:\\jenkins\\workspace\\${WORKSPACE_NAME}\\${PROJECT}\\Mobile_Apps\\Build_Application \nstart /B server_appium.bat \nping 127.0.0.1 -n 300 | find \"Reply\" >nul \n@echo off")
	}
}

deploy.with{

	parameters{
		stringParam("CUSTOM_WORKSPACE","","")
		stringParam("application_name","","Application name of APK")
		stringParam("git_url","","URL of the git repository that contains the project")
		stringParam("source_name","","Folder name of the codes to build ( e.g, src )")
		stringParam("test_name","","Folder name of the codes for Functional test")
		stringParam("ip","","Public IP of your server")
	}
	label("WindowsSlave")
	steps {
      nexusArtifactUploader {
        nexusVersion('nexus2')
        protocol('http')
        nexusUrl('$ip/nexus/content/repositories/releases')
		groupId('mobile.application.apk')
        version('0.0.${BUILD_NUMBER}')
        repository('releases')
		credentialsId('7fb37f64-d099-496e-aae6-954677137357')
        artifact {
            artifactId('$application_name')
            type('apk')
            classifier('snapshot')
            file('$CUSTOM_WORKSPACE\\build\\outputs\\apk\\Build_Application-release.apk')
        }
      }
    }
}
	