output "graphql_api_url" {
  description = "The GraphQL endpoint URL for the room-booking API."
  value       = aws_appsync_graphql_api.this.uris["GRAPHQL"]
}

output "graphql_api_key" {
  description = "The API key used to authenticate against the GraphQL endpoint."
  value       = aws_appsync_api_key.this.key
  sensitive   = true
}
