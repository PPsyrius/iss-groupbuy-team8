variable "name_prefix" {
  description = "Course-scoped resource name prefix"
  type        = string
}

variable "alert_email_addresses" {
  description = "Operator-supplied team recipients; each SNS subscription needs email confirmation"
  type        = set(string)
  default     = []
}

variable "oldest_message_age_seconds" {
  description = "Notification queue age alarm threshold"
  type        = number
  default     = 60

  validation {
    condition     = var.oldest_message_age_seconds > 0
    error_message = "oldest_message_age_seconds must be positive."
  }
}
