
CREATE DATABASE words;


CREATE TABLE IF NOT EXISTS public.phrases
(
    phrase text COLLATE pg_catalog."default"
)

TABLESPACE pg_default;

ALTER TABLE public.phrases
    OWNER to postgres;