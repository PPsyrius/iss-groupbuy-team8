# These blocks are retained as a record of which existing resources this stack owns.
# Review the full import-only plan before applying it to the remote state.
import {
  to = aws_s3_bucket.frontend
  id = "blocbuy-dev-frontend-402178232802"
}

import {
  to = aws_s3_bucket_policy.frontend
  id = "blocbuy-dev-frontend-402178232802"
}

import {
  to = aws_s3_bucket_public_access_block.frontend
  id = "blocbuy-dev-frontend-402178232802"
}

import {
  to = aws_cloudfront_origin_access_control.frontend
  id = var.oac_id
}

import {
  to = aws_cloudfront_function.spa_fallback
  id = "blocbuy-dev-spa-fallback"
}

import {
  to = aws_cloudfront_response_headers_policy.security
  id = "3c91a2c1-f130-42e8-b9cc-fec6a682c838"
}

import {
  to = aws_cloudfront_distribution.dev
  id = "E3U5W7UWDL8LRV"
}

import {
  to = aws_iam_role.github_deploy
  id = "BlocBuyGitHubFrontendDeployDev"
}

import {
  to = aws_iam_role_policy.github_deploy
  id = "BlocBuyGitHubFrontendDeployDev:frontend-s3-cloudfront-deploy"
}
