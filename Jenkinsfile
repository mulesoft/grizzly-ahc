@Library('mule-runtime-library@DEL-3987') _ 
Map pipelineParams = [ "devBranchesRegex" : "support/1_14-mule",
                       "enableAfterReleaseVersionUpdateStage" : false, // Disable stage till DEL-3987 is fixed
                       "projectType" : "Runtime" ]

runtimeBuild(pipelineParams)
