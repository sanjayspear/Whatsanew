CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE listing (
    id                  uuid PRIMARY KEY,
    title               jsonb        NOT NULL,           -- {"en": "...", "kn": "..."}
    summary             jsonb,
    category            text         NOT NULL,           -- dotted taxonomy key
    location            geography(Point, 4326) NOT NULL,
    region_code         text         NOT NULL,           -- IN-KA-BLR (partition key at scale)
    time_zone           text         NOT NULL,           -- IANA zone of the venue
    address             text,
    price_level         smallint CHECK (price_level BETWEEN 1 AND 4),
    price_amount_minor  bigint CHECK (price_amount_minor >= 0),
    price_currency      char(3),
    starts_at           timestamptz,
    ends_at             timestamptz,
    phone_e164          text,
    website             text,
    hero_image_url      text,
    video_id            text,
    status              text         NOT NULL,
    first_listed_at     timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    search_doc          tsvector GENERATED ALWAYS AS (
        jsonb_to_tsvector('simple', title, '["string"]') ||
        jsonb_to_tsvector('simple', coalesce(summary, '{}'::jsonb), '["string"]')
    ) STORED,
    CONSTRAINT event_window_valid CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT price_has_currency CHECK ((price_amount_minor IS NULL) = (price_currency IS NULL))
);

CREATE INDEX listing_location_gix   ON listing USING gist (location) WHERE status = 'PUBLISHED';
CREATE INDEX listing_category_idx   ON listing (category text_pattern_ops);
CREATE INDEX listing_search_doc_gin ON listing USING gin (search_doc);
CREATE INDEX listing_event_idx      ON listing (starts_at, ends_at) WHERE starts_at IS NOT NULL;
CREATE INDEX listing_fresh_idx      ON listing (first_listed_at DESC);

CREATE TABLE outbox (
    id           uuid PRIMARY KEY,
    topic        text        NOT NULL,
    message_key  text        NOT NULL,
    event_type   text        NOT NULL,
    payload      jsonb       NOT NULL,
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX outbox_unpublished_idx ON outbox (occurred_at) WHERE published_at IS NULL;
