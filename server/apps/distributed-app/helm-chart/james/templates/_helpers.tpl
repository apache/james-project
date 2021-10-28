{{/*
Builds up the JAVA_TOOL_OPTIONS env variable for java tunning James
*/}}
{{- define "james.jvmOpts" -}}
{{- if .Values.james.env.glowroot.enabled }}
{{- printf "%s -javaagent:/root/glowroot/glowroot.jar" .Values.james.env.jvmOpts }}
{{- else }}
{{- .Values.james.env.jvmOpts }}
{{- end }}
{{- end }}