package io.magnetic.vamp_core.rest_api.notification

import io.vamp.common.notification.{DefaultPackageMessageResolverProvider, LoggingNotificationProvider}

trait RestApiNotificationProvider extends LoggingNotificationProvider with DefaultPackageMessageResolverProvider