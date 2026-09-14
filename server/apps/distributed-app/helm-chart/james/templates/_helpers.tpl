{{/*
Builds up the JAVA_TOOL_OPTIONS env variable for java tunning James
*/}}
{{- define "james.jvmOpts" -}}
{{- .Values.james.env.jvmOpts }}
{{- end }}