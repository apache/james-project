{{/*
Contruct a temporary URL for Cassandra. This URL ends with a comma.
*/}}
{{- define "cassandra.url.list" -}}
  {{- $port := "9042" }}
  {{- $ns := .Values.cassandra.namespace }}

  {{- if eq .Values.cassandra.deploy "as-pod" }}
    {{- $count :=  int .Values.cassandra.replicaCount }}
    {{- range $index := until $count }}
      {{- printf "cassandra-%d.%s:%s," $index $ns $port }}
    {{- end }}
  {{- else }}
    {{- range $ip := .Values.cassandra.ips }}
      {{- printf "%s:%s," $ip $port }}
    {{- end }}
  {{- end }}
{{- end -}}


{{/*
Contruct a temporary URLfor Elastic Search. This URL ends with a comma
*/}}
{{- define "elasticsearch.url.list" -}}
  {{- $port := "9200" }}
  {{- $ns := .Values.cassandra.namespace }}

  {{- if eq .Values.elasticsearch.deploy "as-pod" }}
    {{- $count :=  int .Values.elasticsearch.replicaCount }}
    {{- range $index := until $count }}
      {{- printf "elasticsearch-%d.%s:%s," $index $ns $port }}
    {{- end }}
  {{- else }}
    {{- range $ip := .Values.elasticsearch.ips }}
      {{- printf "%s:%s," $ip $port }}
    {{- end }}
  {{- end }}
{{- end -}}
