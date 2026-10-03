-- Sukoon followers (A6): share your glucose with family and friends, read-only for them.
-- Run once in Supabase → SQL Editor (safe to run again). Row Level Security does all the
-- guarding: the app only ever holds the publishable key plus the signed-in user's token.

-- People ------------------------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 60),
  created_at timestamptz not null default now()
);

-- A profile row for every new account, named from the sign-up form.
create or replace function public.handle_new_user() returns trigger
  language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, left(coalesce(new.raw_user_meta_data ->> 'display_name', ''), 60))
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Readings ----------------------------------------------------------------------------------------
create table if not exists public.readings (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  ts timestamptz not null,
  mg_dl smallint not null check (mg_dl between 20 and 600),
  trend smallint not null default 0 check (trend between -2 and 2), -- falling fast … rising fast
  primary key (user_id, ts)
);

-- Who follows whom. Rows are only ever created by redeem_invite(): the code is the owner's consent.
create table if not exists public.follows (
  owner uuid not null references auth.users (id) on delete cascade,
  follower uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (owner, follower),
  check (owner <> follower)
);
create index if not exists follows_follower_idx on public.follows (follower);

-- One-time invite codes, 24 h.
create table if not exists public.invites (
  code text primary key check (code ~ '^[A-Z0-9]{8}$'),
  owner uuid not null default auth.uid() references auth.users (id) on delete cascade,
  expires_at timestamptz not null default now() + interval '24 hours',
  used_by uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

-- Row Level Security --------------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.readings enable row level security;
alter table public.follows enable row level security;
alter table public.invites enable row level security;

-- Does the signed-in user follow p_owner? (security definer so policies can use it without recursion)
create or replace function public.follows_owner(p_owner uuid) returns boolean
  language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.follows where owner = p_owner and follower = auth.uid())
$$;

-- Is p_follower one of the signed-in user's followers?
create or replace function public.is_my_follower(p_follower uuid) returns boolean
  language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.follows where owner = auth.uid() and follower = p_follower)
$$;

drop policy if exists "profiles: me and my people" on public.profiles;
create policy "profiles: me and my people" on public.profiles for select to authenticated
  using (id = auth.uid() or public.follows_owner(id) or public.is_my_follower(id));
drop policy if exists "profiles: insert mine" on public.profiles;
create policy "profiles: insert mine" on public.profiles for insert to authenticated with check (id = auth.uid());
drop policy if exists "profiles: update mine" on public.profiles;
create policy "profiles: update mine" on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists "readings: mine and the people I follow" on public.readings;
-- One subquery (not a function call per row): 14 days of 1-minute readings is ~20k rows.
create policy "readings: mine and the people I follow" on public.readings for select to authenticated
  using (user_id = (select auth.uid()) or user_id in (select f.owner from public.follows f where f.follower = (select auth.uid())));
drop policy if exists "readings: insert mine" on public.readings;
create policy "readings: insert mine" on public.readings for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "readings: delete mine" on public.readings;
create policy "readings: delete mine" on public.readings for delete to authenticated using (user_id = auth.uid());

drop policy if exists "follows: either side sees it" on public.follows;
create policy "follows: either side sees it" on public.follows for select to authenticated
  using (owner = auth.uid() or follower = auth.uid());
drop policy if exists "follows: either side ends it" on public.follows;
create policy "follows: either side ends it" on public.follows for delete to authenticated
  using (owner = auth.uid() or follower = auth.uid());

drop policy if exists "invites: my own" on public.invites;
create policy "invites: my own" on public.invites for all to authenticated
  using (owner = auth.uid()) with check (owner = auth.uid());

-- Redeeming a code makes the caller a follower of its owner.
create or replace function public.redeem_invite(p_code text) returns uuid
  language plpgsql security definer set search_path = public as $$
declare
  v_owner uuid;
begin
  if auth.uid() is null then
    raise exception 'Sign in first';
  end if;
  update public.invites
     set used_by = auth.uid()
   where code = upper(regexp_replace(p_code, '[^A-Za-z0-9]', '', 'g'))
     and used_by is null
     and expires_at > now()
     and owner <> auth.uid()
  returning owner into v_owner;
  if v_owner is null then
    raise exception 'That code is wrong, used or expired';
  end if;
  insert into public.follows (owner, follower) values (v_owner, auth.uid()) on conflict do nothing;
  return v_owner;
end $$;

revoke all on function public.redeem_invite(text) from public, anon;
grant execute on function public.redeem_invite(text) to authenticated;
revoke all on function public.follows_owner(uuid) from public, anon;
grant execute on function public.follows_owner(uuid) to authenticated;
revoke all on function public.is_my_follower(uuid) from public, anon;
grant execute on function public.is_my_follower(uuid) to authenticated;

-- Live updates for web followers (Supabase Realtime respects the policies above).
do $$
begin
  alter publication supabase_realtime add table public.readings;
exception when duplicate_object then null;
end $$;
