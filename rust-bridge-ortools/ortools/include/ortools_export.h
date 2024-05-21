
#ifndef ORTOOLS_EXPORT_H
#define ORTOOLS_EXPORT_H

#ifdef ORTOOLS_STATIC_DEFINE
#  define ORTOOLS_EXPORT
#  define ORTOOLS_NO_EXPORT
#else
#  ifndef ORTOOLS_EXPORT
#    ifdef ortools_EXPORTS
        /* We are building this library */
#      define ORTOOLS_EXPORT 
#    else
        /* We are using this library */
#      define ORTOOLS_EXPORT 
#    endif
#  endif

#  ifndef ORTOOLS_NO_EXPORT
#    define ORTOOLS_NO_EXPORT 
#  endif
#endif

#ifndef ORTOOLS_DEPRECATED
#  define ORTOOLS_DEPRECATED __declspec(deprecated)
#endif

#ifndef ORTOOLS_DEPRECATED_EXPORT
#  define ORTOOLS_DEPRECATED_EXPORT ORTOOLS_EXPORT ORTOOLS_DEPRECATED
#endif

#ifndef ORTOOLS_DEPRECATED_NO_EXPORT
#  define ORTOOLS_DEPRECATED_NO_EXPORT ORTOOLS_NO_EXPORT ORTOOLS_DEPRECATED
#endif

#if 0 /* DEFINE_NO_DEPRECATED */
#  ifndef ORTOOLS_NO_DEPRECATED
#    define ORTOOLS_NO_DEPRECATED
#  endif
#endif

#endif /* ORTOOLS_EXPORT_H */
