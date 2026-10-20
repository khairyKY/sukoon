-- "I'm on it": a follower answers an alarm about someone they follow, so the wearer knows someone saw
-- it and the other followers don't all call. A follower may add rows only about people they follow,
-- as themselves; the wearer and everyone following them read them. The name is copied from the
-- follower's profile by the server (other followers can't read each other's profiles), never sent.
-- The app only shows the last half hour. Until this runs, the button says it couldn't send and
-- nothing else changes.
create table if not exists public.responses (
  id bigint generated always as identity primary key,
  owner uuid not null references auth.users (id) on delete cascade,
  follower uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null default '',
  kind text not null check (char_length(kind) <= 20), -- the alarm answered (URGENT_LOW, LOW, ...)
  at timestamptz not null default now()
);
create index if not exists responses_owner_at_idx on public.responses (owner, at desc);

create or replace function public.responses_fill() returns trigger
  language plpgsql security definer set search_path = public as $$
begin
  new.follower := auth.uid();
  new.at := now();
  new.name := coalesce((select display_name from public.profiles where id = auth.uid()), '');
  return new;
end $$;

drop trigger if exists responses_fill on public.responses;
create trigger responses_fill before insert on public.responses
  for each row execute function public.responses_fill();

alter table public.responses enable row level security;

drop policy if exists "responses: I answer for people I follow" on public.responses;
create policy "responses: I answer for people I follow" on public.responses for insert to authenticated
  with check (follower = auth.uid() and public.follows_owner(owner));
drop policy if exists "responses: the wearer and their followers" on public.responses;
create policy "responses: the wearer and their followers" on public.responses for select to authenticated
  using (owner = (select auth.uid()) or public.follows_owner(owner));
