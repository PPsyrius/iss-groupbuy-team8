mock_provider "aws" {}

run "three_demo_rows" {
  command = plan
  module { source = "../modules/observability" }
  variables {
    name                       = "blocbuy-swe5001"
    aws_region                 = "ap-southeast-1"
    cloudfront_distribution_id = "E123EXAMPLE"
    alb_arn_suffix             = "app/blocbuy-swe5001/1234567890abcdef"
    cluster_name               = "blocbuy-swe5001"
    target_groups = {
      commerce-svc     = "targetgroup/commerce/111"
      identity-svc     = "targetgroup/identity/222"
      notification-svc = "targetgroup/notification/333"
    }
    topic_name  = "blocbuy-swe5001-domain-events"
    queue_names = { notification = "blocbuy-swe5001-notification", audit = "blocbuy-swe5001-audit" }
    dlq_names   = { notification = "blocbuy-swe5001-notification-dlq", audit = "blocbuy-swe5001-audit-dlq" }
  }
  assert {
    condition     = [for w in jsondecode(output.dashboard_body).widgets : w.y] == [0, 0, 0, 6, 6, 12, 12] && toset([for w in jsondecode(output.dashboard_body).widgets : split(" - ", w.properties.title)[0]]) == toset(["Edge", "Services", "Messaging"])
    error_message = "Only the three retained demo rows may be created."
  }
  assert {
    condition     = alltrue([for w in jsondecode(output.dashboard_body).widgets : alltrue([for m in w.properties.metrics : can(m[0]) && can(m[1]) && length(m) >= 5])])
    error_message = "Every metric must remain a nested CloudWatch metric array, not flattened scalars."
  }
  assert {
    condition     = jsondecode(output.dashboard_body).widgets[0].properties.region == "us-east-1" && alltrue([for m in jsondecode(output.dashboard_body).widgets[0].properties.metrics : m[5] == "Global" && m[3] == "E123EXAMPLE"]) && alltrue([for w in slice(jsondecode(output.dashboard_body).widgets, 1, 7) : w.properties.region == "ap-southeast-1"])
    error_message = "CloudFront uses the persistent distribution ID, Global dimension and us-east-1 metric region."
  }
  assert {
    condition     = [for w in jsondecode(output.dashboard_body).widgets : length(w.properties.metrics)] == [2, 7, 6, 3, 6, 5, 2]
    error_message = "All three target groups/services and both queues/DLQs must be represented."
  }
  assert {
    condition     = alltrue([for m in jsondecode(output.dashboard_body).widgets[3].properties.metrics : m[0] == "ECS/ContainerInsights" && m[1] == "RunningTaskCount" && m[3] == "blocbuy-swe5001"]) && alltrue([for m in jsondecode(output.dashboard_body).widgets[4].properties.metrics : m[0] == "AWS/ECS" && contains(["CPUUtilization", "MemoryUtilization"], m[1])])
    error_message = "Services show Container Insights task counts and percentage CPU/memory metrics, not raw usage."
  }
  assert {
    condition     = alltrue([for m in jsondecode(output.dashboard_body).widgets[2].properties.metrics : m[1] == "TargetResponseTime" && contains(["p50", "p95"], m[6].stat) && m[3] == "app/blocbuy-swe5001/1234567890abcdef" && contains(values(var.target_groups), m[5])]) && alltrue([for m in jsondecode(output.dashboard_body).widgets[6].properties.metrics : m[1] == "ApproximateAgeOfOldestMessage" && m[4].stat == "Maximum" && contains(values(var.queue_names), m[3])])
    error_message = "Latency/age charts contain only the intended seconds metrics with exact resource dimensions and statistics."
  }
  assert {
    condition     = jsondecode(output.dashboard_body).periodOverride == "inherit" && alltrue([for w in jsondecode(output.dashboard_body).widgets : w.properties.period == 60])
    error_message = "Dashboard traffic must keep the per-minute period used by the calibrated scaling target."
  }
}
