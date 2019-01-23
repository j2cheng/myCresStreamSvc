/*
 * Crestron RTSP Utility Helpers
 */

#ifndef CRESRTSP_UTIL_H
#define CRESRTSP_UTIL_H

#include <assert.h>
#include <errno.h>
#include <stddef.h>
#include <stdarg.h>
#include <inttypes.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/uio.h>



// ***
#define LOCALXSTRINGIFY(s) #s
#define LOCALSTRINGIFY(s) LOCALXSTRINGIFY(s)

// #pragma message ("SIZE_MAX=" LOCALSTRINGIFY(SIZE_MAX))
// #pragma message ("UINT_MAX=" LOCALSTRINGIFY(UINT_MAX))
// #pragma message ("ULONG_MAX=" LOCALSTRINGIFY(ULONG_MAX))

#ifndef SIZE_MAX
#pragma message ("SIZE_MAX is undefined")
#define MF_DEFINED_SIZE_MAX     1
#define SIZE_MAX ULONG_MAX
#pragma message ("new SIZE_MAX=" LOCALSTRINGIFY(SIZE_MAX))
#endif

#ifndef static_assert
#define static_assert(a,b)
#endif
// ***



/* sanity checks required for some macros */
#if __SIZEOF_POINTER__ != 4 && __SIZEOF_POINTER__ != 8
#error "Pointer size is neither 4 nor 8 bytes"
#endif

/* gcc attributes; look them up for more information */
#define _shl_printf_(_a, _b) __attribute__((__format__(printf, _a, _b)))
#define _shl_alloc_(...) __attribute__((__alloc_size__(__VA_ARGS__)))
#define _shl_sentinel_ __attribute__((__sentinel__))
#define _shl_noreturn_ __attribute__((__noreturn__))
#define _shl_unused_ __attribute__((__unused__))
#define _shl_pure_ __attribute__((__pure__))
#define _shl_const_ __attribute__((__const__))
#define _shl_deprecated_ __attribute__((__deprecated__))
#define _shl_packed_ __attribute__((__packed__))
#define _shl_malloc_ __attribute__((__malloc__))
#define _shl_weak_ __attribute__((__weak__))
#define _shl_likely_(_val) (__builtin_expect(!!(_val), 1))
#define _shl_unlikely_(_val) (__builtin_expect(!!(_val), 0))
#define _shl_public_ __attribute__((__visibility__("default")))
#define _shl_hidden_ __attribute__((__visibility__("hidden")))
#define _shl_weakref_(_val) __attribute__((__weakref__(#_val)))
#define _shl_cleanup_(_val) __attribute__((__cleanup__(_val)))

static inline void shl_freep(void *p)
{
	free(*(void**)p);
}

#define _shl_free_ _shl_cleanup_(shl_freep)

static inline void shl_closep(int *p)
{
	if (*p >= 0)
		close(*p);
}

#define _shl_close_ _shl_cleanup_(shl_closep)

static inline void shl_set_errno(int *r)
{
	errno = *r;
}

#define SHL_PROTECT_ERRNO \
	_shl_cleanup_(shl_set_errno) _shl_unused_ int shl__errno = errno

/* 2-level stringify helper */
#define SHL__STRINGIFY(_val) #_val
#define SHL_STRINGIFY(_val) SHL__STRINGIFY(_val)

/* 2-level concatenate helper */
#define SHL__CONCATENATE(_a, _b) _a ## _b
#define SHL_CONCATENATE(_a, _b) SHL__CONCATENATE(_a, _b)

/* unique identifier with prefix */
#define SHL_UNIQUE(_prefix) SHL_CONCATENATE(_prefix, __COUNTER__)

/* array element count */
#define SHL_ARRAY_LENGTH(_array) (sizeof(_array)/sizeof(*(_array)))

/* get parent pointer by container-type, member and member-pointer */
#define shl_container_of(_ptr, _type, _member) \
	({ \
		const typeof( ((_type *)0)->_member ) *__mptr = (const typeof( ((_type *)0)->_member ) *)(_ptr); \
		(_type *)( (char *)__mptr - offsetof(_type, _member) ); \
	})

/* return maximum of two values and do strict type checking */
#define shl_max(_a, _b) \
	({ \
		typeof(_a) __a = (_a); \
		typeof(_b) __b = (_b); \
		(void) (&__a == &__b); \
		__a > __b ? __a : __b; \
	})

/* same as shl_max() but perform explicit cast beforehand */
#define shl_max_t(_type, _a, _b) \
	({ \
		_type __a = (_type)(_a); \
		_type __b = (_type)(_b); \
		__a > __b ? __a : __b; \
	})

/* return minimum of two values and do strict type checking */
#define shl_min(_a, _b) \
	({ \
		typeof(_a) __a = (_a); \
		typeof(_b) __b = (_b); \
		(void) (&__a == &__b); \
		__a < __b ? __a : __b; \
	})

/* same as shl_min() but perform explicit cast beforehand */
#define shl_min_t(_type, _a, _b) \
	({ \
		_type __a = (_type)(_a); \
		_type __b = (_type)(_b); \
		__a < __b ? __a : __b; \
	})

/* clamp value between low and high barriers */
#define shl_clamp(_val, _low, _high) \
	({ \
		typeof(_val) __v = (_val); \
		typeof(_low) __l = (_low); \
		typeof(_high) __h = (_high); \
		(void) (&__v == &__l); \
		(void) (&__v == &__h); \
		((__v > __h) ? __h : ((__v < __l) ? __l : __v)); \
	})

/* align to next higher power-of-2 (except for: 0 => 0, overflow => 0) */
static inline size_t SHL_ALIGN_POWER2(size_t u)
{
	unsigned int shift;

	/* clz(0) is undefined */
	if (u == 1)
		return 1;

	shift = sizeof(unsigned long long) * 8ULL - __builtin_clzll(u - 1ULL);
	return 1ULL << shift;
}

/* zero memory or type */
#define shl_memzero(_ptr, _size) (memset((_ptr), 0, (_size)))
#define shl_zero(_ptr) (shl_memzero(&(_ptr), sizeof(_ptr)))

/* ptr <=> uint casts */
#define SHL_PTR_TO_TYPE(_type, _ptr) ((_type)((uintptr_t)(_ptr)))
#define SHL_TYPE_TO_PTR(_type, _int) ((void*)((uintptr_t)(_int)))
#define SHL_PTR_TO_INT(_ptr) SHL_PTR_TO_TYPE(int, (_ptr))
#define SHL_INT_TO_PTR(_ptr) SHL_TYPE_TO_PTR(int, (_ptr))
#define SHL_PTR_TO_UINT(_ptr) SHL_PTR_TO_TYPE(unsigned int, (_ptr))
#define SHL_UINT_TO_PTR(_ptr) SHL_TYPE_TO_PTR(unsigned int, (_ptr))
#define SHL_PTR_TO_LONG(_ptr) SHL_PTR_TO_TYPE(long, (_ptr))
#define SHL_LONG_TO_PTR(_ptr) SHL_TYPE_TO_PTR(long, (_ptr))
#define SHL_PTR_TO_ULONG(_ptr) SHL_PTR_TO_TYPE(unsigned long, (_ptr))
#define SHL_ULONG_TO_PTR(_ptr) SHL_TYPE_TO_PTR(unsigned long, (_ptr))
#define SHL_PTR_TO_S32(_ptr) SHL_PTR_TO_TYPE(int32_t, (_ptr))
#define SHL_S32_TO_PTR(_ptr) SHL_TYPE_TO_PTR(int32_t, (_ptr))
#define SHL_PTR_TO_U32(_ptr) SHL_PTR_TO_TYPE(uint32_t, (_ptr))
#define SHL_U32_TO_PTR(_ptr) SHL_TYPE_TO_PTR(uint32_t, (_ptr))
#define SHL_PTR_TO_S64(_ptr) SHL_PTR_TO_TYPE(int64_t, (_ptr))
#define SHL_S64_TO_PTR(_ptr) SHL_TYPE_TO_PTR(int64_t, (_ptr))
#define SHL_PTR_TO_U64(_ptr) SHL_PTR_TO_TYPE(uint64_t, (_ptr))
#define SHL_U64_TO_PTR(_ptr) SHL_TYPE_TO_PTR(uint64_t, (_ptr))

/* compile-time assertions */
#define shl_assert_cc(_expr) static_assert(_expr, #_expr)

/*
 * Safe Multiplications
 * Multiplications are subject to overflows. These helpers guarantee that the
 * multiplication can be done safely and return -ERANGE if not.
 *
 * Note: This is horribly slow for ull/uint64_t as we need a division to test
 * for overflows. Take that into account when using these. For smaller integers,
 * we can simply use an upcast-multiplication which gcc should be smart enough
 * to optimize.
 */

#define SHL__REAL_MULT(_max, _val, _factor) \
	({ \
		(_factor == 0 || *(_val) <= (_max) / (_factor)) ? \
			((*(_val) *= (_factor)), 0) : \
			-ERANGE; \
	})

#define SHL__UPCAST_MULT(_type, _max, _val, _factor) \
	({ \
		_type v = *(_val) * (_type)(_factor); \
		(v <= (_max)) ? \
			((*(_val) = v), 0) : \
			-ERANGE; \
	})

static inline int shl_mult_ull(unsigned long long *val,
			       unsigned long long factor)
{
	return SHL__REAL_MULT(ULLONG_MAX, val, factor);
}

static inline int shl_mult_ul(unsigned long *val, unsigned long factor)
{
#if ULONG_MAX < ULLONG_MAX
	return SHL__UPCAST_MULT(unsigned long long, ULONG_MAX, val, factor);
#else
	shl_assert_cc(sizeof(unsigned long) == sizeof(unsigned long long));
	return shl_mult_ull((unsigned long long*)val, factor);
#endif
}

static inline int shl_mult_u(unsigned int *val, unsigned int factor)
{
#if UINT_MAX < ULONG_MAX
	return SHL__UPCAST_MULT(unsigned long, UINT_MAX, val, factor);
#elif UINT_MAX < ULLONG_MAX
	return SHL__UPCAST_MULT(unsigned long long, UINT_MAX, val, factor);
#else
	shl_assert_cc(sizeof(unsigned int) == sizeof(unsigned long long));
	return shl_mult_ull(val, factor);
#endif
}



// ***
#ifndef UINT8_MAX
#define UINT8_MAX (255U)
#endif
#ifndef UINT16_MAX
#define UINT16_MAX (65535U)
#endif
#ifndef UINT32_MAX
#define UINT32_MAX (4294967295U)
#endif
#ifndef UINT64_MAX
#define UINT64_MAX (__UINT64_C(18446744073709551615))
#endif
// ***



static inline int shl_mult_u64(uint64_t *val, uint64_t factor)
{
	return SHL__REAL_MULT(UINT64_MAX, val, factor);
}

static inline int shl_mult_u32(uint32_t *val, uint32_t factor)
{
	return SHL__UPCAST_MULT(uint_fast64_t, UINT32_MAX, val, factor);
}

static inline int shl_mult_u16(uint16_t *val, uint16_t factor)
{
	return SHL__UPCAST_MULT(uint_fast32_t, UINT16_MAX, val, factor);
}

static inline int shl_mult_u8(uint8_t *val, uint8_t factor)
{
	return SHL__UPCAST_MULT(uint_fast16_t, UINT8_MAX, val, factor);
}



/* double linked list */

struct shl_dlist {
	struct shl_dlist *next;
	struct shl_dlist *prev;
};

#define SHL_DLIST_INIT(head) { &(head), &(head) }

static inline void shl_dlist_init(struct shl_dlist *list)
{
	list->next = list;
	list->prev = list;
}

static inline void shl_dlist__link(struct shl_dlist *prev,
					struct shl_dlist *next,
					struct shl_dlist *n)
{
	next->prev = n;
	n->next = next;
	n->prev = prev;
	prev->next = n;
}

static inline void shl_dlist_link(struct shl_dlist *head,
					struct shl_dlist *n)
{
	return shl_dlist__link(head, head->next, n);
}

static inline void shl_dlist_link_tail(struct shl_dlist *head,
					struct shl_dlist *n)
{
	return shl_dlist__link(head->prev, head, n);
}

static inline void shl_dlist__unlink(struct shl_dlist *prev,
					struct shl_dlist *next)
{
	next->prev = prev;
	prev->next = next;
}

static inline void shl_dlist_unlink(struct shl_dlist *e)
{
	if (e->prev && e->next) {
		shl_dlist__unlink(e->prev, e->next);
		e->prev = NULL;
		e->next = NULL;
	}
}

static inline bool shl_dlist_linked(struct shl_dlist *e)
{
	return e->next && e->prev;
}

static inline bool shl_dlist_empty(struct shl_dlist *head)
{
	return head->next == head;
}

static inline struct shl_dlist *shl_dlist_first(struct shl_dlist *head)
{
	return head->next;
}

static inline struct shl_dlist *shl_dlist_last(struct shl_dlist *head)
{
	return head->prev;
}

#define shl_dlist_entry(ptr, type, member) \
	shl_container_of((ptr), type, member)

#define shl_dlist_first_entry(head, type, member) \
	shl_dlist_entry(shl_dlist_first(head), type, member)

#define shl_dlist_last_entry(head, type, member) \
	shl_dlist_entry(shl_dlist_last(head), type, member)

#define shl_dlist_for_each(iter, head) \
	for (iter = (head)->next; iter != (head); iter = iter->next)

#define shl_dlist_for_each_but_one(iter, start, head) \
	for (iter = ((start)->next == (head)) ? \
				(start)->next->next : \
				(start)->next; \
	     iter != (start); \
	     iter = (iter->next == (head) && (start) != (head)) ? \
				iter->next->next : \
				iter->next)

#define shl_dlist_for_each_safe(iter, tmp, head) \
	for (iter = (head)->next, tmp = iter->next; iter != (head); \
		iter = tmp, tmp = iter->next)

#define shl_dlist_for_each_reverse(iter, head) \
	for (iter = (head)->prev; iter != (head); iter = iter->prev)

#define shl_dlist_for_each_reverse_but_one(iter, start, head) \
	for (iter = ((start)->prev == (head)) ? \
				(start)->prev->prev : \
				(start)->prev; \
	     iter != (start); \
	     iter = (iter->prev == (head) && (start) != (head)) ? \
				iter->prev->prev : \
				iter->prev)

#define shl_dlist_for_each_reverse_safe(iter, tmp, head) \
	for (iter = (head)->prev, tmp = iter->prev; iter != (head); \
		iter = tmp, tmp = iter->prev)



/* miscellaneous */

#define shl_htable_entry(pointer, type, member) \
	shl_container_of((pointer), type, member)

/* htable */

struct shl_htable_int {
	size_t (*rehash)(const void *elem, void *priv);
	void *priv;
	unsigned int bits;
	size_t elems, deleted, max, max_with_deleted;
	/* These are the bits which are the same in all pointers. */
	uintptr_t common_mask, common_bits;
	uintptr_t perfect_bit;
	uintptr_t *table;
};

struct shl_htable {
	bool (*compare) (const void *a, const void *b);
	struct shl_htable_int htable;
};

#define SHL_HTABLE_INIT(_obj, _compare, _rehash, _priv)		\
	{							\
		.compare = (_compare),				\
		.htable = {					\
			.rehash = (_rehash),			\
			.priv = (_priv),			\
			.bits = 0,				\
			.elems = 0,				\
			.deleted = 0,				\
			.max = 0,				\
			.max_with_deleted = 0,			\
			.common_mask = -1,			\
			.common_bits = 0,			\
			.perfect_bit = 0,			\
			.table = &(_obj).htable.perfect_bit	\
		}						\
	}

void shl_htable_init(struct shl_htable *htable,
		     bool (*compare) (const void *a, const void *b),
		     size_t (*rehash)(const void *elem, void *priv),
		     void *priv);
void shl_htable_clear(struct shl_htable *htable,
		      void (*free_cb) (void *elem, void *ctx),
		      void *ctx);
void shl_htable_visit(struct shl_htable *htable,
		      void (*visit_cb) (void *elem, void *ctx),
		      void *ctx);
bool shl_htable_lookup(struct shl_htable *htable, const void *obj, size_t hash,
		       void **out);
int shl_htable_insert(struct shl_htable *htable, const void *obj, size_t hash);
bool shl_htable_remove(struct shl_htable *htable, const void *obj, size_t hash,
		       void **out);

size_t shl_htable_this_or_next(struct shl_htable *htable, size_t i);
void *shl_htable_get_entry(struct shl_htable *htable, size_t i);

#define SHL_HTABLE_FOREACH(_iter, _ht) for ( \
		size_t htable__i = shl_htable_this_or_next((_ht), 0); \
		(_iter = (struct rtsp_message *)shl_htable_get_entry((_ht), htable__i)); \
		htable__i = shl_htable_this_or_next((_ht), htable__i + 1) \
	)

#define SHL_HTABLE_FOREACH_MACRO(_iter, _ht, _accessor) for ( \
		size_t htable__i = shl_htable_this_or_next((_ht), 0); \
		(_iter = (struct rtsp_message *)shl_htable_get_entry((_ht), htable__i), \
		 _iter = _iter ? _accessor((void*)_iter) : NULL); \
		htable__i = shl_htable_this_or_next((_ht), htable__i + 1) \
	)

#define SHL_HTABLE_FIRST(_ht) \
	shl_htable_get_entry((_ht), shl_htable_this_or_next((_ht), 0))

#define SHL_HTABLE_FIRST_MACRO(_ht, _accessor) ({ \
		void *htable__i = (void *)shl_htable_get_entry((_ht), \
					shl_htable_this_or_next((_ht), 0)); \
		htable__i ? _accessor((const uint64_t *)htable__i) : NULL; })

/* uint htables */

#if SIZE_MAX < UINT_MAX
#  error "'size_t' is smaller than 'unsigned int'"
#endif

bool shl_htable_compare_uint(const void *a, const void *b);
size_t shl_htable_rehash_uint(const void *elem, void *priv);

#define SHL_HTABLE_INIT_UINT(_obj)					\
	SHL_HTABLE_INIT((_obj), shl_htable_compare_uint,		\
				shl_htable_rehash_uint,			\
				NULL)

static inline void shl_htable_init_uint(struct shl_htable *htable)
{
	shl_htable_init(htable, shl_htable_compare_uint,
			shl_htable_rehash_uint, NULL);
}

static inline void shl_htable_clear_uint(struct shl_htable *htable,
					 void (*cb) (unsigned int *elem,
					             void *ctx),
					 void *ctx)
{
	shl_htable_clear(htable, (void (*) (void*, void*))cb, ctx);
}

static inline void shl_htable_visit_uint(struct shl_htable *htable,
					 void (*cb) (unsigned int *elem,
					             void *ctx),
					 void *ctx)
{
	shl_htable_visit(htable, (void (*) (void*, void*))cb, ctx);
}

static inline bool shl_htable_lookup_uint(struct shl_htable *htable,
					  unsigned int key,
					  unsigned int **out)
{
	return shl_htable_lookup(htable, (const void*)&key, (size_t)key,
				 (void**)out);
}

static inline int shl_htable_insert_uint(struct shl_htable *htable,
					 const unsigned int *key)
{
	return shl_htable_insert(htable, (const void*)key, (size_t)*key);
}

static inline bool shl_htable_remove_uint(struct shl_htable *htable,
					  unsigned int key,
					  unsigned int **out)
{
	return shl_htable_remove(htable, (const void*)&key, (size_t)key,
				 (void**)out);
}

/* ulong htables */

#if SIZE_MAX < ULONG_MAX
#  error "'size_t' is smaller than 'unsigned long'"
#endif

bool shl_htable_compare_ulong(const void *a, const void *b);
size_t shl_htable_rehash_ulong(const void *elem, void *priv);

#define SHL_HTABLE_INIT_ULONG(_obj)					\
	SHL_HTABLE_INIT((_obj), shl_htable_compare_ulong,		\
				shl_htable_rehash_ulong,		\
				NULL)

static inline void shl_htable_init_ulong(struct shl_htable *htable)
{
	shl_htable_init(htable, shl_htable_compare_ulong,
			shl_htable_rehash_ulong, NULL);
}

static inline void shl_htable_clear_ulong(struct shl_htable *htable,
					  void (*cb) (unsigned long *elem,
					              void *ctx),
					  void *ctx)
{
	shl_htable_clear(htable, (void (*) (void*, void*))cb, ctx);
}

static inline void shl_htable_visit_ulong(struct shl_htable *htable,
					  void (*cb) (unsigned long *elem,
					              void *ctx),
					  void *ctx)
{
	shl_htable_visit(htable, (void (*) (void*, void*))cb, ctx);
}

static inline bool shl_htable_lookup_ulong(struct shl_htable *htable,
					   unsigned long key,
					   unsigned long **out)
{
	return shl_htable_lookup(htable, (const void*)&key, (size_t)key,
				 (void**)out);
}

static inline int shl_htable_insert_ulong(struct shl_htable *htable,
					  const unsigned long *key)
{
	return shl_htable_insert(htable, (const void*)key, (size_t)*key);
}

static inline bool shl_htable_remove_ulong(struct shl_htable *htable,
					   unsigned long key,
					   unsigned long **out)
{
	return shl_htable_remove(htable, (const void*)&key, (size_t)key,
				 (void**)out);
}

/* uint64 htables */

bool shl_htable_compare_u64(const void *a, const void *b);
size_t shl_htable_rehash_u64(const void *elem, void *priv);

static inline size_t shl__htable_rehash_u64(const uint64_t *p)
{
#if SIZE_MAX < UINT64_MAX
	return (size_t)((*p ^ (*p >> 32)) & 0xffffffff);
#else
	return (size_t)*p;
#endif
}

#define SHL_HTABLE_INIT_U64(_obj)					\
	SHL_HTABLE_INIT((_obj), shl_htable_compare_u64,			\
				shl_htable_rehash_u64,			\
				NULL)

static inline void shl_htable_init_u64(struct shl_htable *htable)
{
	shl_htable_init(htable, shl_htable_compare_u64,
			shl_htable_rehash_u64, NULL);
}

static inline void shl_htable_clear_u64(struct shl_htable *htable,
					void (*cb) (uint64_t *elem, void *ctx),
					void *ctx)
{
	shl_htable_clear(htable, (void (*) (void*, void*))cb, ctx);
}

static inline void shl_htable_visit_u64(struct shl_htable *htable,
					void (*cb) (uint64_t *elem, void *ctx),
					void *ctx)
{
	shl_htable_visit(htable, (void (*) (void*, void*))cb, ctx);
}

static inline bool shl_htable_lookup_u64(struct shl_htable *htable,
					 uint64_t key,
					 uint64_t **out)
{
	return shl_htable_lookup(htable, (const void*)&key,
				 shl__htable_rehash_u64(&key),
				 (void**)out);
}

static inline int shl_htable_insert_u64(struct shl_htable *htable,
					const uint64_t *key)
{
	return shl_htable_insert(htable,
				 (const void*)key,
				 shl__htable_rehash_u64(key));
}

static inline bool shl_htable_remove_u64(struct shl_htable *htable,
					 uint64_t key,
					 uint64_t **out)
{
	return shl_htable_remove(htable, (const void*)&key,
				 shl__htable_rehash_u64(&key),
				 (void**)out);
}

/* string htables */

bool shl_htable_compare_str(const void *a, const void *b);
size_t shl_htable_rehash_str(const void *elem, void *priv);

#define SHL_HTABLE_INIT_STR(_obj)					\
	SHL_HTABLE_INIT((_obj), shl_htable_compare_str,			\
				shl_htable_rehash_str,			\
				NULL)

static inline void shl_htable_init_str(struct shl_htable *htable)
{
	shl_htable_init(htable, shl_htable_compare_str,
			shl_htable_rehash_str, NULL);
}

static inline void shl_htable_clear_str(struct shl_htable *htable,
					void (*cb) (char **elem,
					            void *ctx),
					void *ctx)
{
	shl_htable_clear(htable, (void (*) (void*, void*))cb, ctx);
}

static inline void shl_htable_visit_str(struct shl_htable *htable,
					void (*cb) (char **elem,
					            void *ctx),
					void *ctx)
{
	shl_htable_visit(htable, (void (*) (void*, void*))cb, ctx);
}

static inline size_t shl_htable_hash_str(struct shl_htable *htable,
					 const char *str, size_t *hash)
{
	size_t h;

	if (hash && *hash) {
		h = *hash;
	} else {
		h = htable->htable.rehash((const void*)&str, NULL);
		if (hash)
			*hash = h;
	}

	return h;
}

static inline bool shl_htable_lookup_str(struct shl_htable *htable,
					 const char *str, size_t *hash,
					 char ***out)
{
	size_t h;

	h = shl_htable_hash_str(htable, str, hash);
	return shl_htable_lookup(htable, (const void*)&str, h, (void**)out);
}

static inline int shl_htable_insert_str(struct shl_htable *htable,
					char **str, size_t *hash)
{
	size_t h;

	h = shl_htable_hash_str(htable, *str, hash);
	return shl_htable_insert(htable, (const void*)str, h);
}

static inline bool shl_htable_remove_str(struct shl_htable *htable,
					 const char *str, size_t *hash,
					 char ***out)
{
	size_t h;

	h = shl_htable_hash_str(htable, str, hash);
	return shl_htable_remove(htable, (const void*)&str, h, (void **)out);
}



/* shl_log */

enum log_severity {
#ifndef LOG_FATAL
	LOG_FATAL = 0,
#endif
#ifndef LOG_ALERT
	LOG_ALERT = 1,
#endif
#ifndef LOG_CRITICAL
	LOG_CRITICAL = 2,
#endif
#ifndef LOG_ERROR
	LOG_ERROR = 3,
#endif
#ifndef LOG_WARNING
	LOG_WARNING = 4,
#endif
#ifndef LOG_NOTICE
	LOG_NOTICE = 5,
#endif
#ifndef LOG_INFO
	LOG_INFO = 6,
#endif
#ifndef LOG_DEBUG
	LOG_DEBUG = 7,
#endif
#ifndef LOG_TRACE
	LOG_TRACE = 8,
#endif
	LOG_SEV_NUM,
};

/*
 * Max Severity
 * Messages with severities between log_max_sev and LOG_SEV_NUM (exclusive)
 * are not logged, but discarded.
 * Default: LOG_NOTICE
 */

extern unsigned int log_max_sev;

/*
 * Defines the debug configuration for gstreamer
 */
extern char* gst_debug;

/*
 * Timestamping
 * Call this to initialize timestamps and cause all log-messages to be prefixed
 * with a timestamp. If not called, no timestamps are added.
 */

void log_init_time(void);

void init_log_codes(void);

/*
 * Log-Functions
 * These functions pass a log-message to the log-subsystem. Handy helpers are
 * provided below. You almost never use these directly.
 *
 * log_submit:
 * Submit the message to the log-subsystem. This is the backend of all other
 * loggers.
 *
 * log_format:
 * Same as log_submit but first converts the arguments into a va_list object.
 *
 * log_llog:
 * Same as log_submit but used as connection to llog.
 *
 * log_dummyf:
 * Dummy logger used for gcc var-arg validation.
 */

__attribute__((format(printf, 6, 0)))
void log_submit(const char *file,
		int line,
		const char *func,
		const char *subs,
		unsigned int sev,
		const char *format,
		va_list args);

__attribute__((format(printf, 6, 7)))
void log_format(const char *file,
		int line,
		const char *func,
		const char *subs,
		unsigned int sev,
		const char *format,
		...);

__attribute__((format(printf, 7, 0)))
void log_llog(void *data,
	      const char *file,
	      int line,
	      const char *func,
	      const char *subs,
	      unsigned int sev,
	      const char *format,
	      va_list args);

unsigned int log_parse_arg(char *optarg);

static inline __attribute__((format(printf, 2, 3)))
void log_dummyf(unsigned int sev, const char *format, ...)
{
}

/*
 * Default values
 * All helpers automatically pick-up the file, line, func and subsystem
 * parameters for a log-message. file, line and func are generated with
 * __FILE__, __LINE__ and __func__ and should almost never be replaced.
 * The subsystem is by default an empty string. To overwrite this, add this
 * line to the top of your source file:
 *   #define LOG_SUBSYSTEM "mysubsystem"
 * Then all following log-messages will use this string as subsystem. You can
 * define it before or after including this header.
 *
 * If you want to change one of these, you need to directly use log_submit and
 * log_format. If you want the defaults for file, line and func you can use:
 *   log_format(LOG_DEFAULT_BASE, subsys, sev, format, ...);
 * If you want all default values, use:
 *   log_format(LOG_DEFAULT, sev, format, ...);
 *
 * If you want to change a single value, this is the default line that is used
 * internally. Adjust it to your needs:
 *   log_format(__FILE__, __LINE__, __func__, LOG_SUBSYSTEM, LOG_ERROR,
 *              "your format string: %s %d", "some args", 5, ...);
 *
 * log_printf is the same as log_format(LOG_DEFAULT, sev, format, ...) and is
 * the most basic wrapper that you can use.
 */

#ifndef LOG_SUBSYSTEM
extern const char *LOG_SUBSYSTEM;
#endif

#define LOG_DEFAULT_BASE __FILE__, __LINE__, __func__
#define LOG_DEFAULT LOG_DEFAULT_BASE, LOG_SUBSYSTEM

#define log_printf(sev, format, ...) \
	log_format(LOG_DEFAULT, (sev), (format), ##__VA_ARGS__)

/*
 * Helpers
 * These pick up all the default values and submit the message to the
 * log-subsystem. The log_debug() function produces zero-code if
 * BUILD_ENABLE_DEBUG is not defined. Therefore, it can be heavily used for
 * debugging and will not have any side-effects.
 * Even if disabled, parameters are evaluated! So it only produces zero code
 * if there are no side-effects and the compiler can optimized it away.
 */

#ifdef BUILD_ENABLE_DEBUG
	#define log_debug(format, ...) \
		log_printf(LOG_DEBUG, (format), ##__VA_ARGS__)
	#define log_trace(format, ...) \
		log_printf(LOG_TRACE, (format), ##__VA_ARGS__)
#else
	#define log_debug(format, ...) \
		log_dummyf(LOG_DEBUG, (format), ##__VA_ARGS__)
	#define log_trace(format, ...) \
		log_dummyf(LOG_TRACE, (format), ##__VA_ARGS__)
#endif

#define log_info(format, ...) \
	log_printf(LOG_INFO, (format), ##__VA_ARGS__)
#define log_notice(format, ...) \
	log_printf(LOG_NOTICE, (format), ##__VA_ARGS__)
#define log_warning(format, ...) \
	log_printf(LOG_WARNING, (format), ##__VA_ARGS__)
#define log_error(format, ...) \
	log_printf(LOG_ERROR, (format), ##__VA_ARGS__)
#define log_critical(format, ...) \
	log_printf(LOG_CRITICAL, (format), ##__VA_ARGS__)
#define log_alert(format, ...) \
	log_printf(LOG_ALERT, (format), ##__VA_ARGS__)
#define log_fatal(format, ...) \
	log_printf(LOG_FATAL, (format), ##__VA_ARGS__)

#define log_EINVAL() \
	(log_error("invalid arguments"), -EINVAL)
#define log_vEINVAL() \
	((void)log_EINVAL())

#define log_EFAULT() \
	(log_error("internal operation failed"), -EFAULT)
#define log_vEFAULT() \
	((void)log_EFAULT())

#define log_ENOMEM() \
	(log_error("out of memory"), -ENOMEM)
#define log_vENOMEM() \
	((void)log_ENOMEM())

#define log_EPIPE() \
	(log_error("fd closed unexpectedly"), -EPIPE)
#define log_vEPIPE() \
	((void)log_EPIPE())

#define log_ERRNO() \
	(log_error("syscall failed (%d): %m", errno), -errno)
#define log_vERRNO() \
	((void)log_ERRNO())

#define log_ERR(_r) \
	(errno = -(_r), log_error("syscall failed (%d): %m", (_r)), (_r))
#define log_vERR(_r) \
	((void)log_ERR(_r))

#define log_EUNMANAGED() \
	(log_error("interface unmanaged"), -EFAULT)
#define log_vEUNMANAGED() \
	((void)log_EUNMANAGED())



/* shl_ring */

struct shl_ring {
	uint8_t *buf;		/* buffer or NULL */
	size_t size;		/* actual size of @buf */
	size_t start;		/* start position of ring */
	size_t used;		/* number of actually used bytes */
};

/* flush buffer so it is empty again */
void shl_ring_flush(struct shl_ring *r);

/* flush buffer, free allocated data and reset to initial state */
void shl_ring_clear(struct shl_ring *r);

/* get pointers to buffer data and their length */
size_t shl_ring_peek(struct shl_ring *r, struct iovec *vec);

/* copy data into external linear buffer */
size_t shl_ring_copy(struct shl_ring *r, void *buf, size_t size);

/* push data to the end of the buffer */
int shl_ring_push(struct shl_ring *r, const void *u8, size_t size);

/* pull data from the front of the buffer */
void shl_ring_pull(struct shl_ring *r, size_t size);

/* return size of occupied buffer in bytes */
static inline size_t shl_ring_get_size(struct shl_ring *r)
{
	return r->used;
}



/* strict atoi */

int shl_ctoi(char ch, unsigned int base);

int shl_atoi_ulln(const char *str,
		  size_t len,
		  unsigned int base,
		  const char **next,
		  unsigned long long *out);
int shl_atoi_uln(const char *str,
		 size_t len,
		 unsigned int base,
		 const char **next,
		 unsigned long *out);
int shl_atoi_un(const char *str,
		size_t len,
		unsigned int base,
		const char **next,
		unsigned int *out);
int shl_atoi_zn(const char *str,
		size_t len,
		unsigned int base,
		const char **next,
		size_t *out);

static inline int shl_atoi_ull(const char *str,
			       unsigned int base,
			       const char **next,
			       unsigned long long *out)
{
	return shl_atoi_ulln(str, strlen(str), base, next, out);
}

static inline int shl_atoi_ul(const char *str,
			      unsigned int base,
			      const char **next,
			      unsigned long *out)
{
	return shl_atoi_uln(str, strlen(str), base, next, out);
}

static inline int shl_atoi_u(const char *str,
			     unsigned int base,
			     const char **next,
			     unsigned int *out)
{
	return shl_atoi_un(str, strlen(str), base, next, out);
}

static inline int shl_atoi_z(const char *str,
			     unsigned int base,
			     const char **next,
			     size_t *out)
{
	return shl_atoi_zn(str, strlen(str), base, next, out);
}

/* greedy alloc */

void *shl_greedy_realloc(void **mem, size_t *size, size_t need);
void *shl_greedy_realloc0(void **mem, size_t *size, size_t need);
void *shl_greedy_realloc_t(void **arr, size_t *cnt, size_t need, size_t ts);
void *shl_greedy_realloc0_t(void **arr, size_t *cnt, size_t need, size_t ts);

#define SHL_GREEDY_REALLOC_T(array, count, need) \
	shl_greedy_realloc_t((void**)&(array), &count, need, sizeof(*(array)))
#define SHL_GREEDY_REALLOC0_T(array, count, need) \
	shl_greedy_realloc0_t((void**)&(array), &count, need, sizeof(*(array)))

/* string helpers */

char *shl_strcat(const char *first, const char *second);
_shl_sentinel_ char *shl_strjoin(const char *first, ...);
int shl_strsplit_n(const char *str, size_t len, const char *sep, char ***out);
int shl_strsplit(const char *str, const char *sep, char ***out);

static inline bool shl_isempty(const char *str)
{
	return !str || !*str;
}

static inline char *shl_startswith(const char *str, const char *prefix)
{
	if (!strncmp(str, prefix, strlen(prefix)))
		return (char*)str + strlen(prefix);
	else
		return NULL;
}

/* strv */

void shl_strv_free(char **strv);

static inline void shl_strv_freep(char ***strv)
{
	shl_strv_free(*strv);
}

#define _shl_strv_free_ _shl_cleanup_(shl_strv_freep)

/* quoted strings */

char shl_qstr_unescape_char(char c);
void shl_qstr_decode_n(char *str, size_t length);
int shl_qstr_tokenize_n(const char *str, size_t length, char ***out);
int shl_qstr_tokenize(const char *str, char ***out);
int shl_qstr_join(char **strv, char **out);

/* mkdir */

int shl_mkdir_p(const char *path, mode_t mode);
int shl_mkdir_p_prefix(const char *prefix, const char *path, mode_t mode);

/* time */

uint64_t shl_now(clockid_t clock);

/* ratelimit */

struct shl_ratelimit {
	uint64_t interval;
	uint64_t begin;
	unsigned burst;
	unsigned num;
};

#define SHL_RATELIMIT_DEFINE(_name, _interval, _burst) \
	struct shl_ratelimit _name = { (_interval), (_burst), 0, 0 }

#define SHL_RATELIMIT_INIT(_v, _interval, _burst) do { \
		struct shl_ratelimit *_r = &(_v); \
		_r->interval = (_interval); \
		_r->burst = (_burst); \
		_r->num = 0; \
		_r->begin = 0; \
	} while (false)

#define SHL_RATELIMIT_RESET(_v) do { \
		struct shl_ratelimit *_r = &(_v); \
		_r->num = 0; \
		_r->begin = 0; \
	} while (false)

bool shl_ratelimit_test(struct shl_ratelimit *r);



// ***
// #ifdef MF_DEFINED_SIZE_MAX
// #undef MF_DEFINED_SIZE_MAX
// #undef SIZE_MAX
// #pragma message ("undefined (back) SIZE_MAX=" LOCALSTRINGIFY(SIZE_MAX))
// #endif
// ***



#endif  /* CRESRTSP_UTIL_H */

