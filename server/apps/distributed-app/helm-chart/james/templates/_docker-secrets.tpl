{{- define "imagePullSecret" }}
{{- printf "{\"auths\": {\"%s\": {\"auth\": \"%s\"}}}" .Values.dockerCredentials.registry (printf "%s:%s" .Values.dockerCredentials.username .Values.dockerCredentials.password | b64enc) | b64enc }}
{{- end }}