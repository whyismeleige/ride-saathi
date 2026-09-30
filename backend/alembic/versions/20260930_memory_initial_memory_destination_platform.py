"""initial memory destination platform

Revision ID: 20260930_memory
Revises: 
Create Date: 2026-09-30 20:19:41.155282
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = '20260930_memory'
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


language_code = postgresql.ENUM('en', 'hi', 'te', name='language_code', create_type=False)
user_status = postgresql.ENUM('active', 'disabled', 'deleted', name='user_status', create_type=False)
saved_place_type = postgresql.ENUM('home', 'work', 'custom', name='saved_place_type', create_type=False)
confirmation_mode = postgresql.ENUM('always', 'adaptive', name='confirmation_mode', create_type=False)
alias_source = postgresql.ENUM('user_created', 'learned', 'correction', 'system', name='alias_source', create_type=False)
ride_session_status = postgresql.ENUM('started', 'listening', 'resolving_destination', 'needs_clarification', 'destination_resolved', 'destination_confirmed', 'ready_for_handoff', 'handoff_opened', 'abandoned', 'failed', name='ride_session_status', create_type=False)
destination_source = postgresql.ENUM('saved_place', 'alias', 'maps_search', 'memory', 'context', 'combined', name='destination_source', create_type=False)
handoff_status = postgresql.ENUM('created', 'opened', 'failed', name='handoff_status', create_type=False)
session_event_type = postgresql.ENUM('ride_session_started', 'voice_received', 'destination_resolution_started', 'destination_resolution_success', 'destination_resolution_failed', 'destination_clarification_required', 'destination_confirmed', 'destination_corrected', 'saved_place_used', 'handoff_created', 'handoff_opened', 'handoff_failed', 'session_abandoned', 'session_failed', name='session_event_type', create_type=False)
ENUMS = (language_code, user_status, saved_place_type, confirmation_mode, alias_source, ride_session_status, destination_source, handoff_status, session_event_type,)

def upgrade() -> None:
    for enum in ENUMS:
        enum.create(op.get_bind(), checkfirst=False)
    op.create_table('users',
    sa.Column('name', sa.Text(), nullable=False),
    sa.Column('phone', sa.Text(), nullable=True),
    sa.Column('email', sa.Text(), nullable=True),
    sa.Column('preferred_language', language_code, server_default='en', nullable=False),
    sa.Column('status', user_status, server_default='active', nullable=False),
    sa.Column('onboarding_completed', sa.Boolean(), server_default=sa.text('false'), nullable=False),
    sa.Column('last_active_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_users')),
    sa.UniqueConstraint('email', name=op.f('uq_users_email')),
    sa.UniqueConstraint('phone', name=op.f('uq_users_phone'))
    )
    op.create_index(op.f('ix_users_onboarding_completed'), 'users', ['onboarding_completed'], unique=False)
    op.create_index(op.f('ix_users_status'), 'users', ['status'], unique=False)
    op.create_table('saved_places',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('label', sa.Text(), nullable=False),
    sa.Column('place_type', saved_place_type, server_default='custom', nullable=False),
    sa.Column('address_text', sa.Text(), nullable=False),
    sa.Column('latitude', sa.Numeric(precision=10, scale=7), nullable=False),
    sa.Column('longitude', sa.Numeric(precision=10, scale=7), nullable=False),
    sa.Column('provider_place_id', sa.Text(), nullable=True),
    sa.Column('is_active', sa.Boolean(), server_default=sa.text('true'), nullable=False),
    sa.Column('visit_count', sa.Integer(), server_default='0', nullable=False),
    sa.Column('last_used_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('last_confirmed_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.CheckConstraint('latitude BETWEEN -90 AND 90', name=op.f('ck_saved_places_latitude_range')),
    sa.CheckConstraint('longitude BETWEEN -180 AND 180', name=op.f('ck_saved_places_longitude_range')),
    sa.CheckConstraint('visit_count >= 0', name=op.f('ck_saved_places_visit_count_nonnegative')),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], name=op.f('fk_saved_places_user_id_users'), ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_saved_places')),
    sa.UniqueConstraint('user_id', 'label', name='uq_saved_places_user_id_label')
    )
    op.create_index(op.f('ix_saved_places_is_active'), 'saved_places', ['is_active'], unique=False)
    op.create_index(op.f('ix_saved_places_place_type'), 'saved_places', ['place_type'], unique=False)
    op.create_index(op.f('ix_saved_places_provider_place_id'), 'saved_places', ['provider_place_id'], unique=False)
    op.create_index(op.f('ix_saved_places_user_id'), 'saved_places', ['user_id'], unique=False)
    op.create_table('user_preferences',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('confirmation_mode', confirmation_mode, server_default='always', nullable=False),
    sa.Column('preferred_ride_provider', sa.Text(), server_default='uber', nullable=False),
    sa.Column('preferred_ride_type', sa.Text(), nullable=True),
    sa.Column('auto_confirm_high_confidence', sa.Boolean(), server_default=sa.text('false'), nullable=False),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], name=op.f('fk_user_preferences_user_id_users'), ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_user_preferences')),
    sa.UniqueConstraint('user_id', name=op.f('uq_user_preferences_user_id'))
    )
    op.create_table('place_aliases',
    sa.Column('saved_place_id', sa.Uuid(), nullable=False),
    sa.Column('alias', sa.Text(), nullable=False),
    sa.Column('normalized_alias', sa.Text(), nullable=False),
    sa.Column('language', language_code, nullable=True),
    sa.Column('source', alias_source, server_default='user_created', nullable=False),
    sa.Column('use_count', sa.Integer(), server_default='0', nullable=False),
    sa.Column('last_used_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.CheckConstraint('length(normalized_alias) > 0', name=op.f('ck_place_aliases_alias_not_empty')),
    sa.CheckConstraint('use_count >= 0', name=op.f('ck_place_aliases_use_count_nonnegative')),
    sa.ForeignKeyConstraint(['saved_place_id'], ['saved_places.id'], name=op.f('fk_place_aliases_saved_place_id_saved_places'), ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_place_aliases')),
    sa.UniqueConstraint('saved_place_id', 'normalized_alias', name=op.f('uq_place_aliases_saved_place_id'))
    )
    op.create_index(op.f('ix_place_aliases_normalized_alias'), 'place_aliases', ['normalized_alias'], unique=False)
    op.create_index(op.f('ix_place_aliases_saved_place_id'), 'place_aliases', ['saved_place_id'], unique=False)
    op.create_table('ride_sessions',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('raw_utterance', sa.Text(), nullable=True),
    sa.Column('status', ride_session_status, server_default='started', nullable=False),
    sa.Column('pickup_address_text', sa.Text(), nullable=True),
    sa.Column('pickup_latitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('pickup_longitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('destination_saved_place_id', sa.Uuid(), nullable=True),
    sa.Column('destination_source', destination_source, nullable=True),
    sa.Column('destination_name', sa.Text(), nullable=True),
    sa.Column('destination_address_text', sa.Text(), nullable=True),
    sa.Column('destination_latitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('destination_longitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('resolution_confidence', sa.Numeric(precision=5, scale=4), nullable=True),
    sa.Column('preferred_provider', sa.Text(), nullable=True),
    sa.Column('preferred_product_type', sa.Text(), nullable=True),
    sa.Column('started_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('resolved_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('confirmed_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('handoff_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('ended_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.CheckConstraint('(destination_latitude IS NULL) = (destination_longitude IS NULL)', name=op.f('ck_ride_sessions_destination_pair')),
    sa.CheckConstraint('(pickup_latitude IS NULL) = (pickup_longitude IS NULL)', name=op.f('ck_ride_sessions_pickup_pair')),
    sa.CheckConstraint('destination_latitude BETWEEN -90 AND 90', name=op.f('ck_ride_sessions_destination_latitude_range')),
    sa.CheckConstraint('destination_longitude BETWEEN -180 AND 180', name=op.f('ck_ride_sessions_destination_longitude_range')),
    sa.CheckConstraint('pickup_latitude BETWEEN -90 AND 90', name=op.f('ck_ride_sessions_pickup_latitude_range')),
    sa.CheckConstraint('pickup_longitude BETWEEN -180 AND 180', name=op.f('ck_ride_sessions_pickup_longitude_range')),
    sa.CheckConstraint('resolution_confidence BETWEEN 0 AND 1', name=op.f('ck_ride_sessions_confidence_range')),
    sa.ForeignKeyConstraint(['destination_saved_place_id'], ['saved_places.id'], name=op.f('fk_ride_sessions_destination_saved_place_id_saved_places'), ondelete='SET NULL'),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], name=op.f('fk_ride_sessions_user_id_users'), ondelete='RESTRICT'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_ride_sessions'))
    )
    op.create_index(op.f('ix_ride_sessions_destination_saved_place_id'), 'ride_sessions', ['destination_saved_place_id'], unique=False)
    op.create_index(op.f('ix_ride_sessions_status'), 'ride_sessions', ['status'], unique=False)
    op.create_index(op.f('ix_ride_sessions_user_id'), 'ride_sessions', ['user_id'], unique=False)
    op.create_index('ix_ride_sessions_user_id_started_at', 'ride_sessions', ['user_id', 'started_at'], unique=False)
    op.create_table('destination_resolutions',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('ride_session_id', sa.Uuid(), nullable=False),
    sa.Column('raw_transcript', sa.Text(), nullable=False),
    sa.Column('normalized_query', sa.Text(), nullable=False),
    sa.Column('selected_saved_place_id', sa.Uuid(), nullable=True),
    sa.Column('selected_place_name', sa.Text(), nullable=True),
    sa.Column('selected_address_text', sa.Text(), nullable=True),
    sa.Column('selected_latitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('selected_longitude', sa.Numeric(precision=10, scale=7), nullable=True),
    sa.Column('confidence', sa.Numeric(precision=5, scale=4), nullable=False),
    sa.Column('resolution_method', destination_source, nullable=True),
    sa.Column('was_confirmed', sa.Boolean(), server_default=sa.text('false'), nullable=False),
    sa.Column('was_corrected', sa.Boolean(), server_default=sa.text('false'), nullable=False),
    sa.Column('candidates', postgresql.JSONB(astext_type=sa.Text()), server_default=sa.text("'[]'::jsonb"), nullable=False),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.CheckConstraint("jsonb_typeof(candidates) = 'array' AND jsonb_array_length(candidates) <= 5", name=op.f('ck_destination_resolutions_bounded_candidates')),
    sa.CheckConstraint('(selected_latitude IS NULL) = (selected_longitude IS NULL)', name=op.f('ck_destination_resolutions_coordinate_pair')),
    sa.CheckConstraint('confidence BETWEEN 0 AND 1', name=op.f('ck_destination_resolutions_confidence_range')),
    sa.CheckConstraint('selected_latitude BETWEEN -90 AND 90', name=op.f('ck_destination_resolutions_latitude_range')),
    sa.CheckConstraint('selected_longitude BETWEEN -180 AND 180', name=op.f('ck_destination_resolutions_longitude_range')),
    sa.ForeignKeyConstraint(['ride_session_id'], ['ride_sessions.id'], name=op.f('fk_destination_resolutions_ride_session_id_ride_sessions'), ondelete='RESTRICT'),
    sa.ForeignKeyConstraint(['selected_saved_place_id'], ['saved_places.id'], name=op.f('fk_destination_resolutions_selected_saved_place_id_saved_places'), ondelete='SET NULL'),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], name=op.f('fk_destination_resolutions_user_id_users'), ondelete='RESTRICT'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_destination_resolutions'))
    )
    op.create_index(op.f('ix_destination_resolutions_normalized_query'), 'destination_resolutions', ['normalized_query'], unique=False)
    op.create_index(op.f('ix_destination_resolutions_ride_session_id'), 'destination_resolutions', ['ride_session_id'], unique=False)
    op.create_index(op.f('ix_destination_resolutions_user_id'), 'destination_resolutions', ['user_id'], unique=False)
    op.create_table('handoff_events',
    sa.Column('ride_session_id', sa.Uuid(), nullable=False),
    sa.Column('provider', sa.Text(), nullable=False),
    sa.Column('status', handoff_status, server_default='created', nullable=False),
    sa.Column('handoff_created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.Column('handoff_opened_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('failure_reason', sa.Text(), nullable=True),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.ForeignKeyConstraint(['ride_session_id'], ['ride_sessions.id'], name=op.f('fk_handoff_events_ride_session_id_ride_sessions'), ondelete='RESTRICT'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_handoff_events')),
    sa.UniqueConstraint('ride_session_id', name=op.f('uq_handoff_events_ride_session_id'))
    )
    op.create_table('session_events',
    sa.Column('ride_session_id', sa.Uuid(), nullable=False),
    sa.Column('event_type', session_event_type, nullable=False),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.ForeignKeyConstraint(['ride_session_id'], ['ride_sessions.id'], name=op.f('fk_session_events_ride_session_id_ride_sessions'), ondelete='RESTRICT'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_session_events'))
    )
    op.create_index(op.f('ix_session_events_event_type'), 'session_events', ['event_type'], unique=False)
    op.create_index('ix_session_events_session_created', 'session_events', ['ride_session_id', 'created_at'], unique=False)
    op.create_table('destination_corrections',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('resolution_id', sa.Uuid(), nullable=False),
    sa.Column('raw_query', sa.Text(), nullable=False),
    sa.Column('normalized_query', sa.Text(), nullable=False),
    sa.Column('rejected_candidate', postgresql.JSONB(astext_type=sa.Text()), nullable=True),
    sa.Column('accepted_candidate', postgresql.JSONB(astext_type=sa.Text()), nullable=False),
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.ForeignKeyConstraint(['resolution_id'], ['destination_resolutions.id'], name=op.f('fk_destination_corrections_resolution_id_destination_resolutions'), ondelete='RESTRICT'),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], name=op.f('fk_destination_corrections_user_id_users'), ondelete='RESTRICT'),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_destination_corrections')),
    sa.UniqueConstraint('resolution_id', name=op.f('uq_destination_corrections_resolution_id'))
    )
    op.create_index('ix_destination_corrections_user_query', 'destination_corrections', ['user_id', 'normalized_query'], unique=False)
    op.execute("""
        CREATE FUNCTION prevent_session_event_mutation() RETURNS trigger
        LANGUAGE plpgsql AS $$
        BEGIN
            RAISE EXCEPTION 'session_events is append-only' USING ERRCODE = '23000';
        END; $$
    """)
    op.execute("""
        CREATE TRIGGER session_events_append_only
        BEFORE UPDATE OR DELETE OR TRUNCATE ON session_events
        FOR EACH STATEMENT EXECUTE FUNCTION prevent_session_event_mutation()
    """)



def downgrade() -> None:
    # ### commands auto generated by Alembic - please adjust! ###
    op.drop_index('ix_destination_corrections_user_query', table_name='destination_corrections')
    op.drop_table('destination_corrections')
    op.drop_index('ix_session_events_session_created', table_name='session_events')
    op.drop_index(op.f('ix_session_events_event_type'), table_name='session_events')
    op.drop_table('session_events')
    op.drop_table('handoff_events')
    op.drop_index(op.f('ix_destination_resolutions_user_id'), table_name='destination_resolutions')
    op.drop_index(op.f('ix_destination_resolutions_ride_session_id'), table_name='destination_resolutions')
    op.drop_index(op.f('ix_destination_resolutions_normalized_query'), table_name='destination_resolutions')
    op.drop_table('destination_resolutions')
    op.drop_index('ix_ride_sessions_user_id_started_at', table_name='ride_sessions')
    op.drop_index(op.f('ix_ride_sessions_user_id'), table_name='ride_sessions')
    op.drop_index(op.f('ix_ride_sessions_status'), table_name='ride_sessions')
    op.drop_index(op.f('ix_ride_sessions_destination_saved_place_id'), table_name='ride_sessions')
    op.drop_table('ride_sessions')
    op.drop_index(op.f('ix_place_aliases_saved_place_id'), table_name='place_aliases')
    op.drop_index(op.f('ix_place_aliases_normalized_alias'), table_name='place_aliases')
    op.drop_table('place_aliases')
    op.drop_table('user_preferences')
    op.drop_index(op.f('ix_saved_places_user_id'), table_name='saved_places')
    op.drop_index(op.f('ix_saved_places_provider_place_id'), table_name='saved_places')
    op.drop_index(op.f('ix_saved_places_place_type'), table_name='saved_places')
    op.drop_index(op.f('ix_saved_places_is_active'), table_name='saved_places')
    op.drop_table('saved_places')
    op.drop_index(op.f('ix_users_status'), table_name='users')
    op.drop_index(op.f('ix_users_onboarding_completed'), table_name='users')
    op.drop_table('users')
    op.execute("DROP FUNCTION prevent_session_event_mutation()")
    for enum in reversed(ENUMS):
        enum.drop(op.get_bind(), checkfirst=False)
