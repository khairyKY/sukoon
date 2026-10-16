-- A private read link per person, for Nightscout widgets (Rainmeter, watch faces): the nightscout edge
-- function serves that person's readings in Nightscout's format to whoever has the token, so a
-- laptop widget needs no Nightscout server and no VPN. Only the owner sees or changes their token;
-- making a new one stops the old link at once.
create table if not exists public.feed_tokens (
  user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  token text not null unique,
  created_at timestamptz not null default now()
);

alter table public.feed_tokens enable row level security;

drop policy if exists "feed_tokens: my own" on public.feed_tokens;
create policy "feed_tokens: my own" on public.feed_tokens for select to authenticated using (user_id = auth.uid());

-- A new token (64 hex characters from two random UUIDs), replacing any old one.
create or replace function public.new_feed_token() returns text
language plpgsql security definer set search_path = public as $$
declare
  t text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  insert into public.feed_tokens (user_id, token) values (auth.uid(), t)
  on conflict (user_id) do update set token = excluded.token, created_at = now();
  return t;
end $$;

revoke all on function public.new_feed_token() from public, anon;
grant execute on function public.new_feed_token() to authenticated;
