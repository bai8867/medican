-- 契约切片 H2：仅 recipe 表，供 AdminRecipeController PATCH /status 走真实 BaseMapper + lambdaUpdate。
CREATE TABLE IF NOT EXISTS recipe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    cover_url VARCHAR(512),
    efficacy_summary VARCHAR(255),
    collect_count INT NOT NULL DEFAULT 0,
    season_tags VARCHAR(128),
    constitution_tags VARCHAR(255),
    efficacy_tags VARCHAR(255),
    symptom_tags VARCHAR(512),
    instruction_summary CLOB,
    steps_json VARCHAR(4000),
    contraindication CLOB,
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
