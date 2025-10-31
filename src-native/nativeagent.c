#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <jvmti.h>

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
    jvmtiEnv* jvmti_env;
    jvmtiCapabilities cap;
    const char* prefix ="_cool_prefix_";
    
    if ((*jvm)->GetEnv(jvm,(void **) &jvmti_env, JVMTI_VERSION_1_1)!=0)
    {
        perror("Error while getting jvmti_env\n");
        return 1;
    }

    memset(&cap, 0, sizeof(cap));
    cap.can_set_native_method_prefix = 1;
    cap.can_generate_all_class_hook_events = 1;
    if ((*jvmti_env)->AddCapabilities(jvmti_env, &cap) != JVMTI_ERROR_NONE){
      perror("Cannot add capabilities.");
      return 1;
    } 

    (*jvmti_env)->SetNativeMethodPrefix(jvmti_env,prefix);
    printf("native prefix is set to %s\n", prefix);	

    return 0;
}

