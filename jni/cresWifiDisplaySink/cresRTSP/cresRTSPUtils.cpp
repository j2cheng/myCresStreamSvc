/**
* Copyright (C) 2019 to the present, Crestron Electronics, Inc.
* All rights reserved.
* No part of this software may be reproduced in any form, machine
* or natural, without the express written consent of Crestron Electronics.
*
* \file        
*     cresRTSPUtils.cpp
* \brief
*     Crestron RTSP Utility Helpers
* \author
*     Marek Fiuk
* \date
*     02/01/2019
* \note
*
*
*///////////////////////////////////////////////////////////////////////////////


#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>

#include "cresRTSPUtils.h"


#ifdef BUILD_TEST_APP
void RTSPLog(int level, char * format, ...);
#endif


/* shl_htable */

#define COLD __attribute__((cold))

struct htable {
	/* KEEP IN SYNC WITH "struct shl_htable_int" */
	size_t (*rehash)(const void *elem, void *priv);
	void *priv;
	unsigned int bits;
	size_t elems, deleted, max, max_with_deleted;
	/* These are the bits which are the same in all pointers. */
	uintptr_t common_mask, common_bits;
	uintptr_t perfect_bit;
	uintptr_t *table;
};

#define HTABLE_INITIALIZER(name, rehash, priv)				\
	{ rehash, priv, 0, 0, 0, 0, 0, (uintptr_t)(-1), 0, 0, &name.perfect_bit }

struct htable_iter {
	size_t off;
};

/*
 * INLINE COPY OF ccan/htable.c
 */

/* We use 0x1 as deleted marker. */
#define HTABLE_DELETED (0x1)

/* We clear out the bits which are always the same, and put metadata there. */
static inline uintptr_t get_extra_ptr_bits(const struct htable *ht,
					   uintptr_t e)
{
	return e & ht->common_mask;
}

static inline void *get_raw_ptr(const struct htable *ht, uintptr_t e)
{
	return (void *)((e & ~ht->common_mask) | ht->common_bits);
}

static inline uintptr_t make_hval(const struct htable *ht,
				  const void *p, uintptr_t bits)
{
	return ((uintptr_t)p & ~ht->common_mask) | bits;
}

static inline bool entry_is_valid(uintptr_t e)
{
	return e > HTABLE_DELETED;
}

static inline uintptr_t get_hash_ptr_bits(const struct htable *ht,
					  size_t hash)
{
	/* Shuffling the extra bits (as specified in mask) down the
	 * end is quite expensive.  But the lower bits are redundant, so
	 * we fold the value first. */
	return (hash ^ (hash >> ht->bits))
		& ht->common_mask & ~ht->perfect_bit;
}

static void htable_init(struct htable *ht,
			size_t (*rehash)(const void *elem, void *priv),
			void *priv)
{
	struct htable empty = HTABLE_INITIALIZER(empty, NULL, NULL);
	*ht = empty;
	ht->rehash = rehash;
	ht->priv = priv;
	ht->table = &ht->perfect_bit;
}

static void htable_clear(struct htable *ht,
			 void (*free_cb) (void *entry, void *ctx),
			 void *ctx)
{
	size_t i;

	if (ht->table != &ht->perfect_bit) {
		if (free_cb) {
			for (i = 0; i < (size_t)1 << ht->bits; ++i) {
				if (entry_is_valid(ht->table[i]))
					free_cb(get_raw_ptr(ht, ht->table[i]),
						ctx);
			}
		}

		free((void *)ht->table);
	}

	htable_init(ht, ht->rehash, ht->priv);
}

size_t shl_htable_this_or_next(struct shl_htable *htable, size_t i)
{
	struct htable *ht = (struct htable *)&htable->htable;

	if (ht->table != &ht->perfect_bit)
		for ( ; i < (size_t)1 << ht->bits; ++i)
			if (entry_is_valid(ht->table[i]))
				return i;

	return SIZE_MAX;
}

void *shl_htable_get_entry(struct shl_htable *htable, size_t i)
{
	struct htable *ht = (struct htable *)&htable->htable;

	if (i < (size_t)1 << ht->bits)
		if (entry_is_valid(ht->table[i]))
			return get_raw_ptr(ht, ht->table[i]);

	return NULL;
}

static void htable_visit(struct htable *ht,
			 void (*visit_cb) (void *elem, void *ctx),
			 void *ctx)
{
	size_t i;

	if (visit_cb && ht->table != &ht->perfect_bit) {
		for (i = 0; i < (size_t)1 << ht->bits; ++i) {
			if (entry_is_valid(ht->table[i]))
				visit_cb(get_raw_ptr(ht, ht->table[i]), ctx);
		}
	}
}

static size_t hash_bucket(const struct htable *ht, size_t h)
{
	return h & ((1 << ht->bits)-1);
}

static void *htable_val(const struct htable *ht,
			struct htable_iter *i, size_t hash, uintptr_t perfect)
{
	uintptr_t h2 = get_hash_ptr_bits(ht, hash) | perfect;

	while (ht->table[i->off]) {
		if (ht->table[i->off] != HTABLE_DELETED) {
			if (get_extra_ptr_bits(ht, ht->table[i->off]) == h2)
				return get_raw_ptr(ht, ht->table[i->off]);
		}
		i->off = (i->off + 1) & ((1 << ht->bits)-1);
		h2 &= ~perfect;
	}
	return NULL;
}

static void *htable_firstval(const struct htable *ht,
			     struct htable_iter *i, size_t hash)
{
	i->off = hash_bucket(ht, hash);
	return htable_val(ht, i, hash, ht->perfect_bit);
}

static void *htable_nextval(const struct htable *ht,
			    struct htable_iter *i, size_t hash)
{
	i->off = (i->off + 1) & ((1 << ht->bits)-1);
	return htable_val(ht, i, hash, 0);
}

/* This does not expand the hash table, that's up to caller. */
static void ht_add(struct htable *ht, const void *_new, size_t h)
{
	size_t i;
	uintptr_t perfect = ht->perfect_bit;

	i = hash_bucket(ht, h);

	while (entry_is_valid(ht->table[i])) {
		perfect = 0;
		i = (i + 1) & ((1 << ht->bits)-1);
	}
	ht->table[i] = make_hval(ht, _new, get_hash_ptr_bits(ht, h)|perfect);
}

static COLD bool double_table(struct htable *ht)
{
	unsigned int i;
	size_t oldnum = (size_t)1 << ht->bits;
	uintptr_t *oldtable, e;

	oldtable = ht->table;
	ht->table = (uintptr_t *)calloc(1 << (ht->bits+1), sizeof(size_t));
	if (!ht->table) {
		ht->table = oldtable;
		return false;
	}
	ht->bits++;
	ht->max = ((size_t)3 << ht->bits) / 4;
	ht->max_with_deleted = ((size_t)9 << ht->bits) / 10;

	/* If we lost our "perfect bit", get it back now. */
	if (!ht->perfect_bit && ht->common_mask) {
		for (i = 0; i < sizeof(ht->common_mask) * CHAR_BIT; i++) {
			if (ht->common_mask & ((size_t)1 << i)) {
				ht->perfect_bit = (size_t)1 << i;
				break;
			}
		}
	}

	if (oldtable != &ht->perfect_bit) {
		for (i = 0; i < oldnum; i++) {
			if (entry_is_valid(e = oldtable[i])) {
				void *p = get_raw_ptr(ht, e);
				ht_add(ht, p, ht->rehash(p, ht->priv));
			}
		}
		free(oldtable);
	}
	ht->deleted = 0;
	return true;
}

static COLD void rehash_table(struct htable *ht)
{
	size_t start, i;
	uintptr_t e;

	/* Beware wrap cases: we need to start from first empty bucket. */
	for (start = 0; ht->table[start]; start++);

	for (i = 0; i < (size_t)1 << ht->bits; i++) {
		size_t h = (i + start) & ((1 << ht->bits)-1);
		e = ht->table[h];
		if (!e)
			continue;
		if (e == HTABLE_DELETED)
			ht->table[h] = 0;
		else if (!(e & ht->perfect_bit)) {
			void *p = get_raw_ptr(ht, e);
			ht->table[h] = 0;
			ht_add(ht, p, ht->rehash(p, ht->priv));
		}
	}
	ht->deleted = 0;
}

/* We stole some bits, now we need to put them back... */
static COLD void update_common(struct htable *ht, const void *p)
{
	unsigned int i;
	uintptr_t maskdiff, bitsdiff;

	if (ht->elems == 0) {
		/* Always reveal one bit of the pointer in the bucket,
		 * so it's not zero or HTABLE_DELETED (1), even if
		 * hash happens to be 0.  Assumes (void *)1 is not a
		 * valid pointer. */
		for (i = sizeof(uintptr_t)*CHAR_BIT - 1; i > 0; i--) {
			if ((uintptr_t)p & ((uintptr_t)1 << i))
				break;
		}

		ht->common_mask = ~((uintptr_t)1 << i);
		ht->common_bits = ((uintptr_t)p & ht->common_mask);
		ht->perfect_bit = 1;
		return;
	}

	/* Find bits which are unequal to old common set. */
	maskdiff = ht->common_bits ^ ((uintptr_t)p & ht->common_mask);

	/* These are the bits which go there in existing entries. */
	bitsdiff = ht->common_bits & maskdiff;

	for (i = 0; i < (size_t)1 << ht->bits; i++) {
		if (!entry_is_valid(ht->table[i]))
			continue;
		/* Clear the bits no longer in the mask, set them as
		 * expected. */
		ht->table[i] &= ~maskdiff;
		ht->table[i] |= bitsdiff;
	}

	/* Take away those bits from our mask, bits and perfect bit. */
	ht->common_mask &= ~maskdiff;
	ht->common_bits &= ~maskdiff;
	ht->perfect_bit &= ~maskdiff;
}

static bool htable_add(struct htable *ht, size_t hash, const void *p)
{
	if (ht->elems+1 > ht->max && !double_table(ht))
		return false;
	if (ht->elems+1 + ht->deleted > ht->max_with_deleted)
		rehash_table(ht);
	assert(p);
	if (((uintptr_t)p & ht->common_mask) != ht->common_bits)
		update_common(ht, p);

	ht_add(ht, p, hash);
	ht->elems++;
	return true;
}

static void htable_delval(struct htable *ht, struct htable_iter *i)
{
	assert(i->off < (size_t)1 << ht->bits);
	assert(entry_is_valid(ht->table[i->off]));

	ht->elems--;
	ht->table[i->off] = HTABLE_DELETED;
	ht->deleted++;
}

/*
 * Wrapper code to make it easier to use this hash-table as map.
 */

void shl_htable_init(struct shl_htable *htable,
		     bool (*compare) (const void *a, const void *b),
		     size_t (*rehash)(const void *elem, void *priv),
		     void *priv)
{
	struct htable *ht = (struct htable *)&htable->htable;

	htable->compare = compare;
	htable_init(ht, rehash, priv);
}

void shl_htable_clear(struct shl_htable *htable,
		      void (*free_cb) (void *elem, void *ctx),
		      void *ctx)
{
	struct htable *ht = (struct htable *)&htable->htable;

	htable_clear(ht, free_cb, ctx);
}

void shl_htable_visit(struct shl_htable *htable,
		      void (*visit_cb) (void *elem, void *ctx),
		      void *ctx)
{
	struct htable *ht = (struct htable *)&htable->htable;

	htable_visit(ht, visit_cb, ctx);
}

bool shl_htable_lookup(struct shl_htable *htable, const void *obj, size_t hash,
		       void **out)
{
	struct htable *ht = (struct htable *)&htable->htable;
	struct htable_iter i;
	void *c;

	for (c = htable_firstval(ht, &i, hash);
	     c;
	     c = htable_nextval(ht, &i, hash)) {
		if (htable->compare(obj, c)) {
			if (out)
				*out = c;
			return true;
		}
	}

	return false;
}

int shl_htable_insert(struct shl_htable *htable, const void *obj, size_t hash)
{
	struct htable *ht = (struct htable *)&htable->htable;
	bool b;

	b = htable_add(ht, hash, (void*)obj);
	return b ? 0 : -ENOMEM;
}

bool shl_htable_remove(struct shl_htable *htable, const void *obj, size_t hash,
		       void **out)
{
	struct htable *ht = (struct htable *)&htable->htable;
	struct htable_iter i;
	void *c;

	for (c = htable_firstval(ht, &i, hash);
	     c;
	     c = htable_nextval(ht, &i, hash)) {
		if (htable->compare(obj, c)) {
			if (out)
				*out = c;
			htable_delval(ht, &i);
			return true;
		}
	}

	return false;
}

/*
 * Helpers
 */

bool shl_htable_compare_uint(const void *a, const void *b)
{
	return *(const unsigned int*)a == *(const unsigned int*)b;
}

size_t shl_htable_rehash_uint(const void *elem, void *priv)
{
	return (size_t)*(const unsigned int*)elem;
}

bool shl_htable_compare_ulong(const void *a, const void *b)
{
	return *(const unsigned long*)a == *(const unsigned long*)b;
}

size_t shl_htable_rehash_ulong(const void *elem, void *priv)
{
	return (size_t)*(const unsigned long*)elem;
}

bool shl_htable_compare_u64(const void *a, const void *b)
{
	return *(const uint64_t*)a == *(const uint64_t*)b;
}

size_t shl_htable_rehash_u64(const void *elem, void *priv)
{
	return shl__htable_rehash_u64((const uint64_t*)elem);
}

bool shl_htable_compare_str(const void *a, const void *b)
{
	if (!*(char**)a || !*(char**)b)
		return *(char**)a == *(char**)b;
	else
		return !strcmp(*(char**)a, *(char**)b);
}

/* DJB's hash function */
size_t shl_htable_rehash_str(const void *elem, void *priv)
{
	const char *str = *(char**)elem;
	size_t hash = 5381;

	for ( ; str && *str; ++str)
		hash = (hash << 5) + hash + (size_t)*str;

	return hash;
}



/* shl_log */

/*
 * Locking
 * Dummies to implement locking. If we ever want lock-protected logging, these
 * need to be provided by the user.
 */

static inline void log_lock()
{
}

static inline void log_unlock()
{
}

/*
 * Time Management
 * We print seconds and microseconds since application start for each
 * log-message in case log_init_time() has been called.
 */

static struct timeval log__ftime;

static bool log__have_time(void)
{
	return !(log__ftime.tv_sec == 0 && log__ftime.tv_usec == 0);
}

void log_init_time(void)
{
	if (!log__have_time())
		gettimeofday(&log__ftime, NULL);
}

static void log__time(long long *sec, long long *usec)
{
	struct timeval t;

	/* In case this is called in parallel to log_init_time(), we need to
	 * catch negative time-diffs. Other than that, this can be called
	 * unlocked. */

	gettimeofday(&t, NULL);
	*sec = t.tv_sec - log__ftime.tv_sec;
	*usec = (long long)t.tv_usec - (long long)log__ftime.tv_usec;
	if (*usec < 0) {
		*sec -= 1;
		if (*sec < 0)
			*sec = 0;
		*usec = 1000000 + *usec;
	}
}

/*
 * Default Values
 * Several logging-parameters may be omitted by applications. To provide sane
 * default values we provide constants here.
 *
 * LOG_SUBSYSTEM: By default no subsystem is specified
 */

const char *LOG_SUBSYSTEM = NULL;

/*
 * Max Severity
 * Messages with severities between log_max_sev and LOG_SEV_NUM (exclusive)
 * are not logged, but discarded.
 */

unsigned int log_max_sev = LOG_NOTICE;

char *gst_debug = NULL;

/*
 * Forward declaration so we can use the locked-versions in other functions
 * here. Be careful to avoid deadlocks, though.
 * Also set default log-subsystem to "log" for all logging inside this API.
 */

static void log__submit(const char *file,
			int line,
			const char *func,
			const char *subs,
			unsigned int sev,
			const char *format,
			va_list args);

#define LOG_SUBSYSTEM "log"

/*
 * Basic logger
 * The log__submit function writes the message into the current log-target. It
 * must be called with log__mutex locked.
 * By default the current time elapsed since the first message was logged is
 * prepended to the message. file, line and func information are appended to the
 * message if sev == LOG_DEBUG.
 * The subsystem, if not NULL, is prepended as "SUBS: " to the message and a
 * newline is always appended by default. Multiline-messages are not allowed.
 */
static const char *log__sev2str[LOG_SEV_NUM] = {NULL};
void init_log_codes() {
	log__sev2str[LOG_TRACE] = "TRACE";
	log__sev2str[LOG_DEBUG] = "DEBUG";
	log__sev2str[LOG_INFO] = "INFO";
	log__sev2str[LOG_NOTICE] = "NOTICE";
	log__sev2str[LOG_WARNING] = "WARNING";
	log__sev2str[LOG_ERROR] = "ERROR";
	log__sev2str[LOG_CRITICAL] = "CRITICAL";
	log__sev2str[LOG_ALERT] = "ALERT";
	log__sev2str[LOG_FATAL] = "FATAL";
}

static void log__submit(const char *file,
			int line,
			const char *func,
			const char *subs,
			unsigned int sev,
			const char *format,
			va_list args)
{
	int saved_errno = errno;
	const char *prefix = NULL;
	FILE *out;
	long long sec, usec;

	out = stderr;
	log__time(&sec, &usec);

	if (sev < LOG_SEV_NUM && sev > log_max_sev)
		return;

	if (sev < LOG_SEV_NUM)
		prefix = log__sev2str[sev];

	if (prefix) {
		if (subs) {
			if (log__have_time())
				fprintf(out, "[%.4lld.%.6lld] %s: %s: ",
					sec, usec, prefix, subs);
			else
				fprintf(out, "%s: %s: ", prefix, subs);
		} else {
			if (log__have_time())
				fprintf(out, "[%.4lld.%.6lld] %s: ",
					sec, usec, prefix);
			else
				fprintf(out, "%s: ", prefix);
		}
	} else {
		if (subs) {
			if (log__have_time())
				fprintf(out, "[%.4lld.%.6lld] %s: ",
					sec, usec, subs);
			else
				fprintf(out, "%s: ", subs);
		} else {
			if (log__have_time())
				fprintf(out, "[%.4lld.%.6lld] ", sec, usec);
		}
	}

	errno = saved_errno;
	vfprintf(out, format, args);

	if (sev == LOG_DEBUG || sev <= LOG_WARNING) {
		if (!func)
			func = "<unknown>";
		if (!file)
			file = "<unknown>";
		if (line < 0)
			line = 0;
		fprintf(out, " (%s() in %s:%d)\n", func, file, line);
	} else {
		fprintf(out, "\n");
	}
}

void log_submit(const char *file,
		int line,
		const char *func,
		const char *subs,
		unsigned int sev,
		const char *format,
		va_list args)
{
	int saved_errno = errno;

	log_lock();
	errno = saved_errno;
	log__submit(file, line, func, subs, sev, format, args);
	log_unlock();

	errno = saved_errno;
}

void log_format(const char *file,
		int line,
		const char *func,
		const char *subs,
		unsigned int sev,
		const char *format,
		...)
{
	int saved_errno = errno;
	va_list list;

	va_start(list, format);
	log_lock();
	errno = saved_errno;
	log__submit(file, line, func, subs, sev, format, list);
	log_unlock();
	va_end(list);

	errno = saved_errno;
}

void log_llog(void *data,
	      const char *file,
	      int line,
	      const char *func,
	      const char *subs,
	      unsigned int sev,
	      const char *format,
	      va_list args)
{
	log_submit(file, line, func, subs, sev, format, args);
}

unsigned int log_parse_arg(char *optarg)
{
	unsigned int log_max_sev;
	if(!strcasecmp(optarg, "fatal")) {
		log_max_sev = LOG_FATAL;
	} else if(!strcasecmp(optarg, "alert")) {
		log_max_sev = LOG_ALERT;
	} else if(!strcasecmp(optarg, "critical")) {
		log_max_sev = LOG_CRITICAL;
	} else if(!strcasecmp(optarg, "error")) {
		log_max_sev = LOG_ERROR;
	} else if(!strcasecmp(optarg, "warning")) {
		log_max_sev = LOG_WARNING;
	} else if(!strcasecmp(optarg, "notice")) {
		log_max_sev = LOG_NOTICE;
	} else if(!strcasecmp(optarg, "info")) {
		log_max_sev = LOG_INFO;
	} else if(!strcasecmp(optarg, "debug")) {
		log_max_sev = LOG_DEBUG;
	} else if(!strcasecmp(optarg, "trace")) {
		log_max_sev = LOG_TRACE;
	} else {
		errno = 0;
		char *temp;
		long val = strtoul(optarg, &temp, 0);

		if (temp == optarg || *temp != '\0'
			|| ((val == LONG_MIN || val == LONG_MAX) && errno == ERANGE)) {
			log_error("Could not convert '%s' to long and leftover string is: '%s'\n", optarg, temp);
		}
		if (val > INT_MAX) {
			errno = ERANGE;
			return INT_MAX;
		}
		if (val < INT_MIN) {
			errno = ERANGE;
			return INT_MIN;
		}
		log_max_sev = (unsigned int) val;
	}
	return log_max_sev;
}


/* shl_ring */

/*
 * Ring buffer
 */

#define RING_MASK(_r, _v) ((_v) & ((_r)->size - 1))

void shl_ring_flush(struct shl_ring *r)
{
	r->start = 0;
	r->used = 0;
}

void shl_ring_clear(struct shl_ring *r)
{
	free(r->buf);
	memset(r, 0, sizeof(*r));
}

/*
 * Get data pointers for current ring-buffer data. @vec must be an array of 2
 * iovec objects. They are filled according to the data available in the
 * ring-buffer. 0, 1 or 2 is returned according to the number of iovec objects
 * that were filled (0 meaning buffer is empty).
 *
 * Hint: "struct iovec" is defined in <sys/uio.h> and looks like this:
 *     struct iovec {
 *         void *iov_base;
 *         size_t iov_len;
 *     };
 */
size_t shl_ring_peek(struct shl_ring *r, struct iovec *vec)
{
	if (r->used == 0) {
		return 0;
	} else if (r->start + r->used <= r->size) {
		if (vec) {
			vec[0].iov_base = &r->buf[r->start];
			vec[0].iov_len = r->used;
		}
		return 1;
	} else {
		if (vec) {
			vec[0].iov_base = &r->buf[r->start];
			vec[0].iov_len = r->size - r->start;
			vec[1].iov_base = r->buf;
			vec[1].iov_len = r->used - (r->size - r->start);
		}
		return 2;
	}
}

/*
 * Copy data from the ring buffer into the linear external buffer @buf. Copy
 * at most @size bytes. If the ring buffer size is smaller, copy less bytes and
 * return the number of bytes copied.
 */
size_t shl_ring_copy(struct shl_ring *r, void *buf, size_t size)
{
	size_t l;

	if (size > r->used)
		size = r->used;

	if (size > 0) {
		l = r->size - r->start;
		if (size <= l) {
			memcpy(buf, &r->buf[r->start], size);
		} else {
			memcpy(buf, &r->buf[r->start], l);
			memcpy((uint8_t*)buf + l, r->buf, size - l);
		}
	}

	return size;
}

/*
 * Resize ring-buffer to size @nsize. @nsize must be a power-of-2, otherwise
 * ring operations will behave incorrectly.
 */
static int ring_resize(struct shl_ring *r, size_t nsize)
{
	uint8_t *buf;
	size_t l;

	buf = (uint8_t *)malloc(nsize);
	if (!buf)
		return -ENOMEM;

	if (r->used > 0) {
		l = r->size - r->start;
		if (r->used <= l) {
			memcpy(buf, &r->buf[r->start], r->used);
		} else {
			memcpy(buf, &r->buf[r->start], l);
			memcpy(&buf[l], r->buf, r->used - l);
		}
	}

	free(r->buf);
	r->buf = buf;
	r->size = nsize;
	r->start = 0;

	return 0;
}

/*
 * Resize ring-buffer to provide enough room for @add bytes of new data. This
 * resizes the buffer if it is too small. It returns -ENOMEM on OOM and 0 on
 * success.
 */
static int ring_grow(struct shl_ring *r, size_t add)
{
	size_t need;

	if (r->size - r->used >= add)
		return 0;

	need = r->used + add;
	if (need <= r->used)
		return -ENOMEM;
	else if (need < 4096)
		need = 4096;

	need = SHL_ALIGN_POWER2(need);
	if (need == 0)
		return -ENOMEM;

	return ring_resize(r, need);
}

/*
 * Push @len bytes from @u8 into the ring buffer. The buffer is resized if it
 * is too small. -ENOMEM is returned on OOM, 0 on success.
 */
int shl_ring_push(struct shl_ring *r, const void *u8, size_t size)
{
	int err;
	size_t pos, l;

	if (size == 0)
		return 0;

	err = ring_grow(r, size);
	if (err < 0)
		return err;

	pos = RING_MASK(r, r->start + r->used);
	l = r->size - pos;
	if (l >= size) {
		memcpy(&r->buf[pos], u8, size);
	} else {
		memcpy(&r->buf[pos], u8, l);
		memcpy(r->buf, (const uint8_t*)u8 + l, size - l);
	}

	r->used += size;

	return 0;
}

/*
 * Remove @len bytes from the start of the ring-buffer. Note that we protect
 * against overflows so removing more bytes than available is safe.
 */
void shl_ring_pull(struct shl_ring *r, size_t size)
{
	if (size > r->used)
		size = r->used;

	r->start = RING_MASK(r, r->start + size);
	r->used -= size;
}


/* shl_utils */

/*
 * Strict atoi()
 * These helpers implement a strict version of atoi() (or strtol()). They only
 * parse digit/alpha characters. No whitespace or other characters are parsed.
 * The unsigned-variants explicitly forbid leading +/- signs. Use the signed
 * variants to allow these.
 * Base-prefix parsing is only done if base=0 is requested. Otherwise,
 * base-prefixes are forbidden.
 * The input string must be ASCII compatbile (which includes UTF8).
 *
 * We also always check for overflows and return errors (but continue parsing!)
 * so callers can catch it correctly.
 *
 * Additionally, we allow "length" parameters so strings do not necessarily have
 * to be zero-terminated. We have wrappers which skip this by passing strlen().
 */

char * loc_stpcpy(char * dest, char * src);

int shl_ctoi(char ch, unsigned int base)
{
	unsigned int v;

	switch (ch) {
	case '0'...'9':
		v = ch - '0';
		break;
	case 'a'...'z':
		v = ch - 'a' + 10;
		break;
	case 'A'...'Z':
		v = ch - 'A' + 10;
		break;
	default:
		return -EINVAL;
	}

	if (v >= base)
		return -EINVAL;

	return v;
}

/* figure out base and skip prefix */
static unsigned int shl__skip_base(const char **str, size_t *len)
{
	if (*len > 1) {
		if ((*str)[0] == '0') {
			if (shl_ctoi((*str)[1], 8) >= 0) {
				*str += 1;
				*len -= 1;
				return 8;
			}
		}
	}

	if (*len > 2) {
		if ((*str)[0] == '0' && (*str)[1] == 'x') {
			if (shl_ctoi((*str)[2], 16) >= 0) {
				*str += 2;
				*len -= 2;
				return 16;
			}
		}
	}

	return 10;
}

int shl_atoi_ulln(const char *str,
		  size_t len,
		  unsigned int base,
		  const char **next,
		  unsigned long long *out)
{
	bool huge;
	uint32_t val1;
	unsigned long long val2;
	size_t pos;
	int r, c;

	/* We use u32 as storage first so we have fast mult-overflow checks. We
	 * cast up to "unsigned long long" once we exceed UINT32_MAX. Overflow
	 * checks will get pretty slow for non-power2 bases, though. */

	huge = false;
	val1 = 0;
	val2 = 0;
	r = 0;

	if (base > 36) {
		if (next)
			*next = str;
		if (out)
			*out = 0;
		return -EINVAL;
	}

	if (base == 0)
		base = shl__skip_base(&str, &len);

	for (pos = 0; pos < len; ++pos) {
		c = shl_ctoi(str[pos], base);
		if (c < 0)
			break;

		/* skip calculations on error */
		if (r < 0)
			continue;

		if (!huge) {
			val2 = val1;
			r = shl_mult_u32(&val1, base);
			if (r >= 0 && val1 + c >= val1)
				val1 += c;
			else
				huge = true;
		}

		if (huge) {
			r = shl_mult_ull(&val2, base);
			if (r >= 0 && val2 + c >= val2)
				val2 += c;
		}
	}

	if (next)
		*next = (char*)&str[pos];
	if (out) {
		if (r < 0)
			*out = ULLONG_MAX;
		else if (huge)
			*out = val2;
		else
			*out = val1;
	}

	return r;
}

int shl_atoi_uln(const char *str,
		 size_t len,
		 unsigned int base,
		 const char **next,
		 unsigned long *out)
{
	unsigned long long val;
	int r;

	r = shl_atoi_ulln(str, len, base, next, &val);
	if (r >= 0 && val > ULONG_MAX)
		r = -ERANGE;

	if (out)
		*out = shl_min(val, (unsigned long long)ULONG_MAX);

	return r;
}

int shl_atoi_un(const char *str,
		size_t len,
		unsigned int base,
		const char **next,
		unsigned int *out)
{
	unsigned long long val;
	int r;

	r = shl_atoi_ulln(str, len, base, next, &val);
	if (r >= 0 && val > UINT_MAX)
		r = -ERANGE;

	if (out)
		*out = shl_min(val, (unsigned long long)UINT_MAX);

	return r;
}

int shl_atoi_zn(const char *str,
		size_t len,
		unsigned int base,
		const char **next,
		size_t *out)
{
	unsigned long long val;
	int r;

	r = shl_atoi_ulln(str, len, base, next, &val);
	if (r >= 0 && val > SIZE_MAX)
		r = -ERANGE;

	if (out)
		*out = shl_min(val, (unsigned long long)SIZE_MAX);

	return r;
}

/*
 * Greedy Realloc
 * The greedy-realloc helpers simplify power-of-2 buffer allocations. If you
 * have a dynamic array, simply use shl_greedy_realloc() for re-allocations
 * and it makes sure your buffer-size is always a multiple of 2 and is big
 * enough for your new entries.
 * Default size is 64, but you can initialize your buffer to a bigger default
 * if you need.
 */

void *shl_greedy_realloc(void **mem, size_t *size, size_t need)
{
	size_t nsize;
	void *p;

	if (*size >= need)
		return *mem;

	nsize = SHL_ALIGN_POWER2(shl_max_t(size_t, 64U, need));
	if (nsize == 0)
		return NULL;

	p = realloc(*mem, nsize);
	if (!p)
		return NULL;

	*mem = p;
	*size = nsize;
	return p;
}

void *shl_greedy_realloc0(void **mem, size_t *size, size_t need)
{
	size_t prev = *size;
	uint8_t *p;

	p = (uint8_t *)shl_greedy_realloc(mem, size, need);
	if (!p)
		return NULL;

	if (*size > prev)
		shl_memzero(&p[prev], *size - prev);

	return p;
}

void *shl_greedy_realloc_t(void **arr, size_t *cnt, size_t need, size_t ts)
{
	size_t ncnt;
	void *p;

	if (*cnt >= need)
		return *arr;
	if (!ts)
		return NULL;

	ncnt = SHL_ALIGN_POWER2(shl_max_t(size_t, 64U, need));
	if (ncnt == 0)
		return NULL;

	p = realloc(*arr, ncnt * ts);
	if (!p)
		return NULL;

	*arr = p;
	*cnt = ncnt;
	return p;
}

void *shl_greedy_realloc0_t(void **arr, size_t *cnt, size_t need, size_t ts)
{
	size_t prev = *cnt;
	uint8_t *p;

	p = (uint8_t *)shl_greedy_realloc_t(arr, cnt, need, ts);
	if (!p)
		return NULL;

	if (*cnt > prev)
		shl_memzero(&p[prev * ts], (*cnt - prev) * ts);

	return p;
}

/*
 * String Helpers
 */

char *shl_strcat(const char *first, const char *second)
{
	size_t flen, slen;
	char *str;

	if (!first)
		first = "";
	if (!second)
		second = "";

	flen = strlen(first);
	slen = strlen(second);
	if (flen + slen + 1 <= flen)
		return NULL;

	str = (char *)malloc(flen + slen + 1);
	if (!str)
		return NULL;

	strcpy(str, first);
	strcpy(&str[flen], second);

	return str;
}

char *shl_strjoin(const char *first, ...) {
	va_list args;
	size_t len, l;
	const char *arg;
	char *str, *p;

	va_start(args, first);

	for (arg = first, len = 0; arg; arg = va_arg(args, const char*)) {
		l = strlen(arg);
		if (len + l < len)
			return NULL;

		len += l;
	}

	va_end(args);

	str = (char *)malloc(len + 1);
	if (!str)
		return NULL;

	va_start(args, first);

	for (arg = first, p = str; arg; arg = va_arg(args, const char*))
		p = loc_stpcpy(p, (char *)arg);

	va_end(args);

	*p = 0;
	return str;
}

static int shl__split_push(char ***strv,
			   size_t *strv_num,
			   size_t *strv_size,
			   const char *str,
			   size_t len)
{
	size_t strv_need;
	char *ns;

	strv_need = (*strv_num + 2) * sizeof(**strv);
	if (!shl_greedy_realloc0((void**)strv, strv_size, strv_need))
		return -ENOMEM;

	ns = (char *)malloc(len + 1);
	memcpy(ns, str, len);
	ns[len] = 0;

	(*strv)[*strv_num] = ns;
	*strv_num += 1;

	return 0;
}

int shl_strsplit_n(const char *str, size_t len, const char *sep, char ***out)
{
	char **strv;
	size_t i, j, strv_num, strv_size;
	const char *pos;
	int r;

	if (!out || !sep)
		return -EINVAL;
	if (!str)
		str = "";

	strv_num = 0;
	strv_size = sizeof(*strv);
	strv = (char **)malloc(strv_size);
	if (!strv)
		return -ENOMEM;

	pos = str;

	for (i = 0; i < len; ++i) {
		for (j = 0; sep[j]; ++j) {
			if (str[i] != sep[j])
				continue;

			/* ignore empty tokens */
			if (pos != &str[i]) {
				r = shl__split_push(&strv,
						    &strv_num,
						    &strv_size,
						    pos,
						    &str[i] - pos);
				if (r < 0)
					goto error;
			}

			pos = &str[i + 1];
			break;
		}
	}

	/* copy trailing token if available */
	if (i > 0 && pos != &str[i]) {
		r = shl__split_push(&strv,
				    &strv_num,
				    &strv_size,
				    pos,
				    &str[i] - pos);
		if (r < 0)
			goto error;
	}

	if ((int)strv_num < (ssize_t)strv_num) {
		r = -ENOMEM;
		goto error;
	}

	strv[strv_num] = NULL;
	*out = strv;
	return strv_num;

error:
	for (i = 0; i < strv_num; ++i)
		free(strv[i]);
	free(strv);
	return r;
}

int shl_strsplit(const char *str, const char *sep, char ***out)
{
	return shl_strsplit_n(str, str ? strlen(str) : 0, sep, out);
}

/*
 * strv
 */

void shl_strv_free(char **strv)
{
	unsigned int i;

	if (!strv)
		return;

	for (i = 0; strv[i]; ++i)
		free(strv[i]);

	free(strv);
}

/*
 * Quoted Strings
 */

char shl_qstr_unescape_char(char c)
{
	switch (c) {
	case 'a':
		return '\a';
	case 'b':
		return '\b';
	case 'f':
		return '\f';
	case 'n':
		return '\n';
	case 'r':
		return '\r';
	case 't':
		return '\t';
	case 'v':
		return '\v';
	case '"':
		return '"';
	case '\'':
		return '\'';
	case '\\':
		return '\\';
	default:
		return 0;
	}
}

void shl_qstr_decode_n(char *str, size_t length)
{
	size_t i;
	bool escaped;
	char *pos, c, quoted;

	quoted = 0;
	escaped = false;
	pos = str;

	for (i = 0; i < length; ++i) {
		if (escaped) {
			escaped = false;
			c = shl_qstr_unescape_char(str[i]);
			if (c) {
				*pos++ = c;
			} else if (!str[i]) {
				/* ignore binary 0 */
			} else {
				*pos++ = '\\';
				*pos++ = str[i];
			}
		} else if (quoted) {
			if (str[i] == '\\')
				escaped = true;
			else if (str[i] == '"' && quoted == '"')
				quoted = 0;
			else if (str[i] == '\'' && quoted == '\'')
				quoted = 0;
			else if (!str[i])
				/* ignore binary 0 */ ;
			else
				*pos++ = str[i];
		} else {
			if (str[i] == '\\')
				escaped = true;
			else if (str[i] == '"' || str[i] == '\'')
				quoted = str[i];
			else if (!str[i])
				/* ignore binary 0 */ ;
			else
				*pos++ = str[i];
		}
	}

	if (escaped)
		*pos++ = '\\';

	*pos = 0;
}

static int shl__qstr_push(char ***strv,
			  size_t *strv_num,
			  size_t *strv_size,
			  const char *str,
			  size_t len)
{
	size_t strv_need;
	char *ns;

	strv_need = (*strv_num + 2) * sizeof(**strv);
	if (!shl_greedy_realloc0((void**)strv, strv_size, strv_need))
		return -ENOMEM;

	ns = (char *)malloc(len + 1);
	memcpy(ns, str, len);
	ns[len] = 0;

	shl_qstr_decode_n(ns, len);
	(*strv)[*strv_num] = ns;
	*strv_num += 1;

	return 0;
}

int shl_qstr_tokenize_n(const char *str, size_t length, char ***out)
{
	char **strv, quoted;
	size_t i, strv_num, strv_size;
	const char *pos;
	bool escaped;
	int r;

	if (!out)
		return -EINVAL;
	if (!str)
		str = "";

	strv_num = 0;
	strv_size = sizeof(*strv);
	strv = (char **)malloc(strv_size);
	if (!strv)
		return -ENOMEM;

	quoted = 0;
	escaped = false;
	pos = str;

	for (i = 0; i < length; ++i) {
		if (escaped) {
			escaped = false;
		} else if (str[i] == '\\') {
			escaped = true;
		} else if (quoted) {
			if (str[i] == '"' && quoted == '"')
				quoted = 0;
			else if (str[i] == '\'' && quoted == '\'')
				quoted = 0;
		} else if (str[i] == '"') {
			quoted = '"';
		} else if (str[i] == '\'') {
			quoted = '\'';
		} else if (str[i] == ' ') {
			/* ignore multiple separators */
			if (pos != &str[i]) {
				r = shl__qstr_push(&strv,
						   &strv_num,
						   &strv_size,
						   pos,
						   &str[i] - pos);
				if (r < 0)
					goto error;
			}

			pos = &str[i + 1];
		}
	}

	/* copy trailing token if available */
	if (i > 0 && pos != &str[i]) {
		r = shl__qstr_push(&strv,
				   &strv_num,
				   &strv_size,
				   pos,
				   &str[i] - pos);
		if (r < 0)
			goto error;
	}

	if ((int)strv_num < (ssize_t)strv_num) {
		r = -ENOMEM;
		goto error;
	}

	strv[strv_num] = NULL;
	*out = strv;
	return strv_num;

error:
	for (i = 0; i < strv_num; ++i)
		free(strv[i]);
	free(strv);
	return r;
}

int shl_qstr_tokenize(const char *str, char ***out)
{
	return shl_qstr_tokenize_n(str, str ? strlen(str) : 0, out);
}

size_t shl__qstr_encode(char *dst, const char *src, bool need_quote)
{
	size_t l = 0;

	if (need_quote)
		dst[l++] = '"';

	for ( ; *src; ++src) {
		switch (*src) {
		case '\\':
		case '\"':
			dst[l++] = '\\';
			dst[l++] = *src;
			break;
		default:
			dst[l++] = *src;
			break;
		}
	}

	if (need_quote)
		dst[l++] = '"';

	return l;
}

size_t shl__qstr_length(const char *str, bool *need_quote)
{
	size_t l = 0;

	*need_quote = false;

	do {
		switch (*str++) {
		case 0:
			return l;
		case ' ':
		case '\t':
		case '\n':
		case '\v':
			*need_quote = true;
		}
	} while (++l);

	return l - 1;
}

int shl_qstr_join(char **strv, char **out)
{
	_shl_free_ char *line = NULL;
	size_t len, size, l, need;
	bool need_quote;

	len = 0;
	size = 0;

	if (!SHL_GREEDY_REALLOC_T(line, size, 1))
		return -ENOMEM;

	*line = 0;

	for ( ; *strv; ++strv) {
		l = shl__qstr_length(*strv, &need_quote);

		/* at most 2 byte per char (escapes) */
		if (l * 2 < l)
			return -ENOMEM;
		need = l * 2;

		/* on top of current length */
		if (need + len < need)
			return -ENOMEM;
		need += len;

		/* at most 4 extra chars: 2 quotes + 0 + separator */
		if (need + 4 < len)
			return -ENOMEM;
		need += 4;

		/* make sure line is big enough */
		if (!SHL_GREEDY_REALLOC_T(line, size, need))
			return -ENOMEM;

		if (len)
			line[len++] = ' ';

		len += shl__qstr_encode(line + len, *strv, need_quote);
	}

	if ((size_t)(int)len != len)
		return -ENOMEM;

	line[len] = 0;
	*out = line;
	line = NULL;
	return len;
}

/*
 * mkdir
 */

static int shl__is_dir(const char *path)
{
	struct stat st;

	if (stat(path, &st) < 0)
		return -errno;

	return S_ISDIR(st.st_mode);
}

const char *shl__path_startswith(const char *path, const char *prefix)
{
	size_t pathl, prefixl;

	if (!path)
		return NULL;
	if (!prefix)
		return path;

	if ((path[0] == '/') != (prefix[0] == '/'))
		return NULL;

	/* compare all components */
	while (true) {
		path += strspn(path, "/");
		prefix += strspn(prefix, "/");

		if (*prefix == 0)
			return (char*)path;
		if (*path == 0)
			return NULL;

		pathl = strcspn(path, "/");
		prefixl = strcspn(prefix, "/");
		if (pathl != prefixl || memcmp(path, prefix, pathl))
			return NULL;

		path += pathl;
		prefix += prefixl;
	}
}

int shl__mkdir_parents(const char *prefix, const char *path, mode_t mode)
{
	const char *p, *e;
	char *t;
	int r;

	if (!shl__path_startswith(path, prefix))
		return -ENOTDIR;

	e = strrchr(path, '/');
	if (!e || e == path)
		return 0;

	p = strndup(path, e - path);

	r = shl__is_dir(p);
	if (r > 0)
		return 0;
	if (r == 0)
		return -ENOTDIR;

	t = (char *)alloca(strlen(path) + 1);
	p = path + strspn(path, "/");

	while (true) {
		e = p + strcspn(p, "/");
		p = e + strspn(e, "/");

		if (*p == 0)
			return 0;

		memcpy(t, path, e - path);
		t[e - path] = 0;

		if (prefix && shl__path_startswith(prefix, t))
			continue;

		r = mkdir(t, mode);
		if (r < 0 && errno != EEXIST)
			return -errno;
	}
}

static int shl__mkdir_p(const char *prefix, const char *path, mode_t mode)
{
	int r;

	r = shl__mkdir_parents(prefix, path, mode);
	if (r < 0)
		return r;

	r = mkdir(path, mode);
	if (r < 0 && (errno != EEXIST || shl__is_dir(path) <= 0))
		return -errno;

	return 0;
}

int shl_mkdir_p(const char *path, mode_t mode)
{
	return shl__mkdir_p(NULL, path, mode);
}

int shl_mkdir_p_prefix(const char *prefix, const char *path, mode_t mode)
{
	return shl__mkdir_p(prefix, path, mode);
}

