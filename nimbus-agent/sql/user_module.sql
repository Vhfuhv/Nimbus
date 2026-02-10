-- Nimbus Agent user module schema (MySQL 8+)

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_key` VARCHAR(64) NOT NULL COMMENT 'Business user identifier, e.g. header X-User-Id',
  `display_name` VARCHAR(128) DEFAULT NULL COMMENT 'Optional nickname',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=active,0=disabled',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_user_key` (`user_key`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User master table';

CREATE TABLE IF NOT EXISTS `chat_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `session_id` VARCHAR(128) NOT NULL COMMENT 'Client/session identifier',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'FK -> users.id',
  `memory_key` VARCHAR(256) NOT NULL COMMENT 'Chat memory key: userId:sessionId',
  `last_active_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_sessions_user_session` (`user_id`, `session_id`),
  UNIQUE KEY `uk_chat_sessions_memory_key` (`memory_key`),
  KEY `idx_chat_sessions_last_active` (`last_active_at`),
  CONSTRAINT `fk_chat_sessions_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chat session ownership table';

SET FOREIGN_KEY_CHECKS = 1;

