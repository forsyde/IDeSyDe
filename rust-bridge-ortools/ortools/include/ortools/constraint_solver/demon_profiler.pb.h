// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: ortools/constraint_solver/demon_profiler.proto
// Protobuf C++ Version: 4.25.0

#ifndef GOOGLE_PROTOBUF_INCLUDED_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto_2epb_2eh
#define GOOGLE_PROTOBUF_INCLUDED_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto_2epb_2eh

#include <limits>
#include <string>
#include <type_traits>
#include <utility>

#include "google/protobuf/port_def.inc"
#if PROTOBUF_VERSION < 4025000
#error "This file was generated by a newer version of protoc which is"
#error "incompatible with your Protocol Buffer headers. Please update"
#error "your headers."
#endif  // PROTOBUF_VERSION

#if 4025000 < PROTOBUF_MIN_PROTOC_VERSION
#error "This file was generated by an older version of protoc which is"
#error "incompatible with your Protocol Buffer headers. Please"
#error "regenerate this file with a newer version of protoc."
#endif  // PROTOBUF_MIN_PROTOC_VERSION
#include "google/protobuf/port_undef.inc"
#include "google/protobuf/io/coded_stream.h"
#include "google/protobuf/arena.h"
#include "google/protobuf/arenastring.h"
#include "google/protobuf/generated_message_tctable_decl.h"
#include "google/protobuf/generated_message_util.h"
#include "google/protobuf/metadata_lite.h"
#include "google/protobuf/generated_message_reflection.h"
#include "google/protobuf/message.h"
#include "google/protobuf/repeated_field.h"  // IWYU pragma: export
#include "google/protobuf/extension_set.h"  // IWYU pragma: export
#include "google/protobuf/unknown_field_set.h"
// @@protoc_insertion_point(includes)

// Must be included last.
#include "google/protobuf/port_def.inc"

#define PROTOBUF_INTERNAL_EXPORT_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto

namespace google {
namespace protobuf {
namespace internal {
class AnyMetadata;
}  // namespace internal
}  // namespace protobuf
}  // namespace google

// Internal implementation detail -- do not use these members.
struct TableStruct_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto {
  static const ::uint32_t offsets[];
};
extern const ::google::protobuf::internal::DescriptorTable
    descriptor_table_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto;
namespace operations_research {
class ConstraintRuns;
struct ConstraintRunsDefaultTypeInternal;
extern ConstraintRunsDefaultTypeInternal _ConstraintRuns_default_instance_;
class DemonRuns;
struct DemonRunsDefaultTypeInternal;
extern DemonRunsDefaultTypeInternal _DemonRuns_default_instance_;
}  // namespace operations_research
namespace google {
namespace protobuf {
}  // namespace protobuf
}  // namespace google

namespace operations_research {

// ===================================================================


// -------------------------------------------------------------------

class DemonRuns final :
    public ::google::protobuf::Message /* @@protoc_insertion_point(class_definition:operations_research.DemonRuns) */ {
 public:
  inline DemonRuns() : DemonRuns(nullptr) {}
  ~DemonRuns() override;
  template<typename = void>
  explicit PROTOBUF_CONSTEXPR DemonRuns(::google::protobuf::internal::ConstantInitialized);

  inline DemonRuns(const DemonRuns& from)
      : DemonRuns(nullptr, from) {}
  DemonRuns(DemonRuns&& from) noexcept
    : DemonRuns() {
    *this = ::std::move(from);
  }

  inline DemonRuns& operator=(const DemonRuns& from) {
    CopyFrom(from);
    return *this;
  }
  inline DemonRuns& operator=(DemonRuns&& from) noexcept {
    if (this == &from) return *this;
    if (GetArena() == from.GetArena()
  #ifdef PROTOBUF_FORCE_COPY_IN_MOVE
        && GetArena() != nullptr
  #endif  // !PROTOBUF_FORCE_COPY_IN_MOVE
    ) {
      InternalSwap(&from);
    } else {
      CopyFrom(from);
    }
    return *this;
  }

  inline const ::google::protobuf::UnknownFieldSet& unknown_fields() const
      ABSL_ATTRIBUTE_LIFETIME_BOUND {
    return _internal_metadata_.unknown_fields<::google::protobuf::UnknownFieldSet>(::google::protobuf::UnknownFieldSet::default_instance);
  }
  inline ::google::protobuf::UnknownFieldSet* mutable_unknown_fields()
      ABSL_ATTRIBUTE_LIFETIME_BOUND {
    return _internal_metadata_.mutable_unknown_fields<::google::protobuf::UnknownFieldSet>();
  }

  static const ::google::protobuf::Descriptor* descriptor() {
    return GetDescriptor();
  }
  static const ::google::protobuf::Descriptor* GetDescriptor() {
    return default_instance().GetMetadata().descriptor;
  }
  static const ::google::protobuf::Reflection* GetReflection() {
    return default_instance().GetMetadata().reflection;
  }
  static const DemonRuns& default_instance() {
    return *internal_default_instance();
  }
  static inline const DemonRuns* internal_default_instance() {
    return reinterpret_cast<const DemonRuns*>(
               &_DemonRuns_default_instance_);
  }
  static constexpr int kIndexInFileMessages =
    0;

  friend void swap(DemonRuns& a, DemonRuns& b) {
    a.Swap(&b);
  }
  inline void Swap(DemonRuns* other) {
    if (other == this) return;
  #ifdef PROTOBUF_FORCE_COPY_IN_SWAP
    if (GetArena() != nullptr &&
        GetArena() == other->GetArena()) {
   #else  // PROTOBUF_FORCE_COPY_IN_SWAP
    if (GetArena() == other->GetArena()) {
  #endif  // !PROTOBUF_FORCE_COPY_IN_SWAP
      InternalSwap(other);
    } else {
      ::google::protobuf::internal::GenericSwap(this, other);
    }
  }
  void UnsafeArenaSwap(DemonRuns* other) {
    if (other == this) return;
    ABSL_DCHECK(GetArena() == other->GetArena());
    InternalSwap(other);
  }

  // implements Message ----------------------------------------------

  DemonRuns* New(::google::protobuf::Arena* arena = nullptr) const final {
    return CreateMaybeMessage<DemonRuns>(arena);
  }
  using ::google::protobuf::Message::CopyFrom;
  void CopyFrom(const DemonRuns& from);
  using ::google::protobuf::Message::MergeFrom;
  void MergeFrom( const DemonRuns& from) {
    DemonRuns::MergeImpl(*this, from);
  }
  private:
  static void MergeImpl(::google::protobuf::Message& to_msg, const ::google::protobuf::Message& from_msg);
  public:
  PROTOBUF_ATTRIBUTE_REINITIALIZES void Clear() final;
  bool IsInitialized() const final;

  ::size_t ByteSizeLong() const final;
  const char* _InternalParse(const char* ptr, ::google::protobuf::internal::ParseContext* ctx) final;
  ::uint8_t* _InternalSerialize(
      ::uint8_t* target, ::google::protobuf::io::EpsCopyOutputStream* stream) const final;
  int GetCachedSize() const { return _impl_._cached_size_.Get(); }

  private:
  ::google::protobuf::internal::CachedSize* AccessCachedSize() const final;
  void SharedCtor(::google::protobuf::Arena* arena);
  void SharedDtor();
  void InternalSwap(DemonRuns* other);

  private:
  friend class ::google::protobuf::internal::AnyMetadata;
  static ::absl::string_view FullMessageName() {
    return "operations_research.DemonRuns";
  }
  protected:
  explicit DemonRuns(::google::protobuf::Arena* arena);
  DemonRuns(::google::protobuf::Arena* arena, const DemonRuns& from);
  public:

  static const ClassData _class_data_;
  const ::google::protobuf::Message::ClassData*GetClassData() const final;

  ::google::protobuf::Metadata GetMetadata() const final;

  // nested types ----------------------------------------------------

  // accessors -------------------------------------------------------

  enum : int {
    kStartTimeFieldNumber = 2,
    kEndTimeFieldNumber = 3,
    kDemonIdFieldNumber = 1,
    kFailuresFieldNumber = 4,
  };
  // repeated int64 start_time = 2;
  int start_time_size() const;
  private:
  int _internal_start_time_size() const;

  public:
  void clear_start_time() ;
  ::int64_t start_time(int index) const;
  void set_start_time(int index, ::int64_t value);
  void add_start_time(::int64_t value);
  const ::google::protobuf::RepeatedField<::int64_t>& start_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* mutable_start_time();

  private:
  const ::google::protobuf::RepeatedField<::int64_t>& _internal_start_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* _internal_mutable_start_time();

  public:
  // repeated int64 end_time = 3;
  int end_time_size() const;
  private:
  int _internal_end_time_size() const;

  public:
  void clear_end_time() ;
  ::int64_t end_time(int index) const;
  void set_end_time(int index, ::int64_t value);
  void add_end_time(::int64_t value);
  const ::google::protobuf::RepeatedField<::int64_t>& end_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* mutable_end_time();

  private:
  const ::google::protobuf::RepeatedField<::int64_t>& _internal_end_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* _internal_mutable_end_time();

  public:
  // string demon_id = 1;
  void clear_demon_id() ;
  const std::string& demon_id() const;
  template <typename Arg_ = const std::string&, typename... Args_>
  void set_demon_id(Arg_&& arg, Args_... args);
  std::string* mutable_demon_id();
  PROTOBUF_NODISCARD std::string* release_demon_id();
  void set_allocated_demon_id(std::string* value);

  private:
  const std::string& _internal_demon_id() const;
  inline PROTOBUF_ALWAYS_INLINE void _internal_set_demon_id(
      const std::string& value);
  std::string* _internal_mutable_demon_id();

  public:
  // int64 failures = 4;
  void clear_failures() ;
  ::int64_t failures() const;
  void set_failures(::int64_t value);

  private:
  ::int64_t _internal_failures() const;
  void _internal_set_failures(::int64_t value);

  public:
  // @@protoc_insertion_point(class_scope:operations_research.DemonRuns)
 private:
  class _Internal;

  friend class ::google::protobuf::internal::TcParser;
  static const ::google::protobuf::internal::TcParseTable<
      2, 4, 0,
      46, 2>
      _table_;
  friend class ::google::protobuf::MessageLite;
  friend class ::google::protobuf::Arena;
  template <typename T>
  friend class ::google::protobuf::Arena::InternalHelper;
  using InternalArenaConstructable_ = void;
  using DestructorSkippable_ = void;
  struct Impl_ {

        inline explicit constexpr Impl_(
            ::google::protobuf::internal::ConstantInitialized) noexcept;
        inline explicit Impl_(::google::protobuf::internal::InternalVisibility visibility,
                              ::google::protobuf::Arena* arena);
        inline explicit Impl_(::google::protobuf::internal::InternalVisibility visibility,
                              ::google::protobuf::Arena* arena, const Impl_& from);
    ::google::protobuf::RepeatedField<::int64_t> start_time_;
    mutable ::google::protobuf::internal::CachedSize _start_time_cached_byte_size_;
    ::google::protobuf::RepeatedField<::int64_t> end_time_;
    mutable ::google::protobuf::internal::CachedSize _end_time_cached_byte_size_;
    ::google::protobuf::internal::ArenaStringPtr demon_id_;
    ::int64_t failures_;
    mutable ::google::protobuf::internal::CachedSize _cached_size_;
    PROTOBUF_TSAN_DECLARE_MEMBER
  };
  union { Impl_ _impl_; };
  friend struct ::TableStruct_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto;
};// -------------------------------------------------------------------

class ConstraintRuns final :
    public ::google::protobuf::Message /* @@protoc_insertion_point(class_definition:operations_research.ConstraintRuns) */ {
 public:
  inline ConstraintRuns() : ConstraintRuns(nullptr) {}
  ~ConstraintRuns() override;
  template<typename = void>
  explicit PROTOBUF_CONSTEXPR ConstraintRuns(::google::protobuf::internal::ConstantInitialized);

  inline ConstraintRuns(const ConstraintRuns& from)
      : ConstraintRuns(nullptr, from) {}
  ConstraintRuns(ConstraintRuns&& from) noexcept
    : ConstraintRuns() {
    *this = ::std::move(from);
  }

  inline ConstraintRuns& operator=(const ConstraintRuns& from) {
    CopyFrom(from);
    return *this;
  }
  inline ConstraintRuns& operator=(ConstraintRuns&& from) noexcept {
    if (this == &from) return *this;
    if (GetArena() == from.GetArena()
  #ifdef PROTOBUF_FORCE_COPY_IN_MOVE
        && GetArena() != nullptr
  #endif  // !PROTOBUF_FORCE_COPY_IN_MOVE
    ) {
      InternalSwap(&from);
    } else {
      CopyFrom(from);
    }
    return *this;
  }

  inline const ::google::protobuf::UnknownFieldSet& unknown_fields() const
      ABSL_ATTRIBUTE_LIFETIME_BOUND {
    return _internal_metadata_.unknown_fields<::google::protobuf::UnknownFieldSet>(::google::protobuf::UnknownFieldSet::default_instance);
  }
  inline ::google::protobuf::UnknownFieldSet* mutable_unknown_fields()
      ABSL_ATTRIBUTE_LIFETIME_BOUND {
    return _internal_metadata_.mutable_unknown_fields<::google::protobuf::UnknownFieldSet>();
  }

  static const ::google::protobuf::Descriptor* descriptor() {
    return GetDescriptor();
  }
  static const ::google::protobuf::Descriptor* GetDescriptor() {
    return default_instance().GetMetadata().descriptor;
  }
  static const ::google::protobuf::Reflection* GetReflection() {
    return default_instance().GetMetadata().reflection;
  }
  static const ConstraintRuns& default_instance() {
    return *internal_default_instance();
  }
  static inline const ConstraintRuns* internal_default_instance() {
    return reinterpret_cast<const ConstraintRuns*>(
               &_ConstraintRuns_default_instance_);
  }
  static constexpr int kIndexInFileMessages =
    1;

  friend void swap(ConstraintRuns& a, ConstraintRuns& b) {
    a.Swap(&b);
  }
  inline void Swap(ConstraintRuns* other) {
    if (other == this) return;
  #ifdef PROTOBUF_FORCE_COPY_IN_SWAP
    if (GetArena() != nullptr &&
        GetArena() == other->GetArena()) {
   #else  // PROTOBUF_FORCE_COPY_IN_SWAP
    if (GetArena() == other->GetArena()) {
  #endif  // !PROTOBUF_FORCE_COPY_IN_SWAP
      InternalSwap(other);
    } else {
      ::google::protobuf::internal::GenericSwap(this, other);
    }
  }
  void UnsafeArenaSwap(ConstraintRuns* other) {
    if (other == this) return;
    ABSL_DCHECK(GetArena() == other->GetArena());
    InternalSwap(other);
  }

  // implements Message ----------------------------------------------

  ConstraintRuns* New(::google::protobuf::Arena* arena = nullptr) const final {
    return CreateMaybeMessage<ConstraintRuns>(arena);
  }
  using ::google::protobuf::Message::CopyFrom;
  void CopyFrom(const ConstraintRuns& from);
  using ::google::protobuf::Message::MergeFrom;
  void MergeFrom( const ConstraintRuns& from) {
    ConstraintRuns::MergeImpl(*this, from);
  }
  private:
  static void MergeImpl(::google::protobuf::Message& to_msg, const ::google::protobuf::Message& from_msg);
  public:
  PROTOBUF_ATTRIBUTE_REINITIALIZES void Clear() final;
  bool IsInitialized() const final;

  ::size_t ByteSizeLong() const final;
  const char* _InternalParse(const char* ptr, ::google::protobuf::internal::ParseContext* ctx) final;
  ::uint8_t* _InternalSerialize(
      ::uint8_t* target, ::google::protobuf::io::EpsCopyOutputStream* stream) const final;
  int GetCachedSize() const { return _impl_._cached_size_.Get(); }

  private:
  ::google::protobuf::internal::CachedSize* AccessCachedSize() const final;
  void SharedCtor(::google::protobuf::Arena* arena);
  void SharedDtor();
  void InternalSwap(ConstraintRuns* other);

  private:
  friend class ::google::protobuf::internal::AnyMetadata;
  static ::absl::string_view FullMessageName() {
    return "operations_research.ConstraintRuns";
  }
  protected:
  explicit ConstraintRuns(::google::protobuf::Arena* arena);
  ConstraintRuns(::google::protobuf::Arena* arena, const ConstraintRuns& from);
  public:

  static const ClassData _class_data_;
  const ::google::protobuf::Message::ClassData*GetClassData() const final;

  ::google::protobuf::Metadata GetMetadata() const final;

  // nested types ----------------------------------------------------

  // accessors -------------------------------------------------------

  enum : int {
    kInitialPropagationStartTimeFieldNumber = 2,
    kInitialPropagationEndTimeFieldNumber = 3,
    kDemonsFieldNumber = 5,
    kConstraintIdFieldNumber = 1,
    kFailuresFieldNumber = 4,
  };
  // repeated int64 initial_propagation_start_time = 2;
  int initial_propagation_start_time_size() const;
  private:
  int _internal_initial_propagation_start_time_size() const;

  public:
  void clear_initial_propagation_start_time() ;
  ::int64_t initial_propagation_start_time(int index) const;
  void set_initial_propagation_start_time(int index, ::int64_t value);
  void add_initial_propagation_start_time(::int64_t value);
  const ::google::protobuf::RepeatedField<::int64_t>& initial_propagation_start_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* mutable_initial_propagation_start_time();

  private:
  const ::google::protobuf::RepeatedField<::int64_t>& _internal_initial_propagation_start_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* _internal_mutable_initial_propagation_start_time();

  public:
  // repeated int64 initial_propagation_end_time = 3;
  int initial_propagation_end_time_size() const;
  private:
  int _internal_initial_propagation_end_time_size() const;

  public:
  void clear_initial_propagation_end_time() ;
  ::int64_t initial_propagation_end_time(int index) const;
  void set_initial_propagation_end_time(int index, ::int64_t value);
  void add_initial_propagation_end_time(::int64_t value);
  const ::google::protobuf::RepeatedField<::int64_t>& initial_propagation_end_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* mutable_initial_propagation_end_time();

  private:
  const ::google::protobuf::RepeatedField<::int64_t>& _internal_initial_propagation_end_time() const;
  ::google::protobuf::RepeatedField<::int64_t>* _internal_mutable_initial_propagation_end_time();

  public:
  // repeated .operations_research.DemonRuns demons = 5;
  int demons_size() const;
  private:
  int _internal_demons_size() const;

  public:
  void clear_demons() ;
  ::operations_research::DemonRuns* mutable_demons(int index);
  ::google::protobuf::RepeatedPtrField< ::operations_research::DemonRuns >*
      mutable_demons();
  private:
  const ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>& _internal_demons() const;
  ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>* _internal_mutable_demons();
  public:
  const ::operations_research::DemonRuns& demons(int index) const;
  ::operations_research::DemonRuns* add_demons();
  const ::google::protobuf::RepeatedPtrField< ::operations_research::DemonRuns >&
      demons() const;
  // string constraint_id = 1;
  void clear_constraint_id() ;
  const std::string& constraint_id() const;
  template <typename Arg_ = const std::string&, typename... Args_>
  void set_constraint_id(Arg_&& arg, Args_... args);
  std::string* mutable_constraint_id();
  PROTOBUF_NODISCARD std::string* release_constraint_id();
  void set_allocated_constraint_id(std::string* value);

  private:
  const std::string& _internal_constraint_id() const;
  inline PROTOBUF_ALWAYS_INLINE void _internal_set_constraint_id(
      const std::string& value);
  std::string* _internal_mutable_constraint_id();

  public:
  // int64 failures = 4;
  void clear_failures() ;
  ::int64_t failures() const;
  void set_failures(::int64_t value);

  private:
  ::int64_t _internal_failures() const;
  void _internal_set_failures(::int64_t value);

  public:
  // @@protoc_insertion_point(class_scope:operations_research.ConstraintRuns)
 private:
  class _Internal;

  friend class ::google::protobuf::internal::TcParser;
  static const ::google::protobuf::internal::TcParseTable<
      3, 5, 1,
      56, 2>
      _table_;
  friend class ::google::protobuf::MessageLite;
  friend class ::google::protobuf::Arena;
  template <typename T>
  friend class ::google::protobuf::Arena::InternalHelper;
  using InternalArenaConstructable_ = void;
  using DestructorSkippable_ = void;
  struct Impl_ {

        inline explicit constexpr Impl_(
            ::google::protobuf::internal::ConstantInitialized) noexcept;
        inline explicit Impl_(::google::protobuf::internal::InternalVisibility visibility,
                              ::google::protobuf::Arena* arena);
        inline explicit Impl_(::google::protobuf::internal::InternalVisibility visibility,
                              ::google::protobuf::Arena* arena, const Impl_& from);
    ::google::protobuf::RepeatedField<::int64_t> initial_propagation_start_time_;
    mutable ::google::protobuf::internal::CachedSize _initial_propagation_start_time_cached_byte_size_;
    ::google::protobuf::RepeatedField<::int64_t> initial_propagation_end_time_;
    mutable ::google::protobuf::internal::CachedSize _initial_propagation_end_time_cached_byte_size_;
    ::google::protobuf::RepeatedPtrField< ::operations_research::DemonRuns > demons_;
    ::google::protobuf::internal::ArenaStringPtr constraint_id_;
    ::int64_t failures_;
    mutable ::google::protobuf::internal::CachedSize _cached_size_;
    PROTOBUF_TSAN_DECLARE_MEMBER
  };
  union { Impl_ _impl_; };
  friend struct ::TableStruct_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto;
};

// ===================================================================




// ===================================================================


#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wstrict-aliasing"
#endif  // __GNUC__
// -------------------------------------------------------------------

// DemonRuns

// string demon_id = 1;
inline void DemonRuns::clear_demon_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.demon_id_.ClearToEmpty();
}
inline const std::string& DemonRuns::demon_id() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_get:operations_research.DemonRuns.demon_id)
  return _internal_demon_id();
}
template <typename Arg_, typename... Args_>
inline PROTOBUF_ALWAYS_INLINE void DemonRuns::set_demon_id(Arg_&& arg,
                                                     Args_... args) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.demon_id_.Set(static_cast<Arg_&&>(arg), args..., GetArena());
  // @@protoc_insertion_point(field_set:operations_research.DemonRuns.demon_id)
}
inline std::string* DemonRuns::mutable_demon_id() ABSL_ATTRIBUTE_LIFETIME_BOUND {
  std::string* _s = _internal_mutable_demon_id();
  // @@protoc_insertion_point(field_mutable:operations_research.DemonRuns.demon_id)
  return _s;
}
inline const std::string& DemonRuns::_internal_demon_id() const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.demon_id_.Get();
}
inline void DemonRuns::_internal_set_demon_id(const std::string& value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.demon_id_.Set(value, GetArena());
}
inline std::string* DemonRuns::_internal_mutable_demon_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  return _impl_.demon_id_.Mutable( GetArena());
}
inline std::string* DemonRuns::release_demon_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  // @@protoc_insertion_point(field_release:operations_research.DemonRuns.demon_id)
  return _impl_.demon_id_.Release();
}
inline void DemonRuns::set_allocated_demon_id(std::string* value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.demon_id_.SetAllocated(value, GetArena());
  #ifdef PROTOBUF_FORCE_COPY_DEFAULT_STRING
        if (_impl_.demon_id_.IsDefault()) {
          _impl_.demon_id_.Set("", GetArena());
        }
  #endif  // PROTOBUF_FORCE_COPY_DEFAULT_STRING
  // @@protoc_insertion_point(field_set_allocated:operations_research.DemonRuns.demon_id)
}

// repeated int64 start_time = 2;
inline int DemonRuns::_internal_start_time_size() const {
  return _internal_start_time().size();
}
inline int DemonRuns::start_time_size() const {
  return _internal_start_time_size();
}
inline void DemonRuns::clear_start_time() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.start_time_.Clear();
}
inline ::int64_t DemonRuns::start_time(int index) const {
  // @@protoc_insertion_point(field_get:operations_research.DemonRuns.start_time)
  return _internal_start_time().Get(index);
}
inline void DemonRuns::set_start_time(int index, ::int64_t value) {
  _internal_mutable_start_time()->Set(index, value);
  // @@protoc_insertion_point(field_set:operations_research.DemonRuns.start_time)
}
inline void DemonRuns::add_start_time(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _internal_mutable_start_time()->Add(value);
  // @@protoc_insertion_point(field_add:operations_research.DemonRuns.start_time)
}
inline const ::google::protobuf::RepeatedField<::int64_t>& DemonRuns::start_time() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_list:operations_research.DemonRuns.start_time)
  return _internal_start_time();
}
inline ::google::protobuf::RepeatedField<::int64_t>* DemonRuns::mutable_start_time()
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable_list:operations_research.DemonRuns.start_time)
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  return _internal_mutable_start_time();
}
inline const ::google::protobuf::RepeatedField<::int64_t>& DemonRuns::_internal_start_time()
    const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.start_time_;
}
inline ::google::protobuf::RepeatedField<::int64_t>* DemonRuns::_internal_mutable_start_time() {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return &_impl_.start_time_;
}

// repeated int64 end_time = 3;
inline int DemonRuns::_internal_end_time_size() const {
  return _internal_end_time().size();
}
inline int DemonRuns::end_time_size() const {
  return _internal_end_time_size();
}
inline void DemonRuns::clear_end_time() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.end_time_.Clear();
}
inline ::int64_t DemonRuns::end_time(int index) const {
  // @@protoc_insertion_point(field_get:operations_research.DemonRuns.end_time)
  return _internal_end_time().Get(index);
}
inline void DemonRuns::set_end_time(int index, ::int64_t value) {
  _internal_mutable_end_time()->Set(index, value);
  // @@protoc_insertion_point(field_set:operations_research.DemonRuns.end_time)
}
inline void DemonRuns::add_end_time(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _internal_mutable_end_time()->Add(value);
  // @@protoc_insertion_point(field_add:operations_research.DemonRuns.end_time)
}
inline const ::google::protobuf::RepeatedField<::int64_t>& DemonRuns::end_time() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_list:operations_research.DemonRuns.end_time)
  return _internal_end_time();
}
inline ::google::protobuf::RepeatedField<::int64_t>* DemonRuns::mutable_end_time()
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable_list:operations_research.DemonRuns.end_time)
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  return _internal_mutable_end_time();
}
inline const ::google::protobuf::RepeatedField<::int64_t>& DemonRuns::_internal_end_time()
    const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.end_time_;
}
inline ::google::protobuf::RepeatedField<::int64_t>* DemonRuns::_internal_mutable_end_time() {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return &_impl_.end_time_;
}

// int64 failures = 4;
inline void DemonRuns::clear_failures() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.failures_ = ::int64_t{0};
}
inline ::int64_t DemonRuns::failures() const {
  // @@protoc_insertion_point(field_get:operations_research.DemonRuns.failures)
  return _internal_failures();
}
inline void DemonRuns::set_failures(::int64_t value) {
  _internal_set_failures(value);
  // @@protoc_insertion_point(field_set:operations_research.DemonRuns.failures)
}
inline ::int64_t DemonRuns::_internal_failures() const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.failures_;
}
inline void DemonRuns::_internal_set_failures(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.failures_ = value;
}

// -------------------------------------------------------------------

// ConstraintRuns

// string constraint_id = 1;
inline void ConstraintRuns::clear_constraint_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.constraint_id_.ClearToEmpty();
}
inline const std::string& ConstraintRuns::constraint_id() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_get:operations_research.ConstraintRuns.constraint_id)
  return _internal_constraint_id();
}
template <typename Arg_, typename... Args_>
inline PROTOBUF_ALWAYS_INLINE void ConstraintRuns::set_constraint_id(Arg_&& arg,
                                                     Args_... args) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.constraint_id_.Set(static_cast<Arg_&&>(arg), args..., GetArena());
  // @@protoc_insertion_point(field_set:operations_research.ConstraintRuns.constraint_id)
}
inline std::string* ConstraintRuns::mutable_constraint_id() ABSL_ATTRIBUTE_LIFETIME_BOUND {
  std::string* _s = _internal_mutable_constraint_id();
  // @@protoc_insertion_point(field_mutable:operations_research.ConstraintRuns.constraint_id)
  return _s;
}
inline const std::string& ConstraintRuns::_internal_constraint_id() const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.constraint_id_.Get();
}
inline void ConstraintRuns::_internal_set_constraint_id(const std::string& value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.constraint_id_.Set(value, GetArena());
}
inline std::string* ConstraintRuns::_internal_mutable_constraint_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  return _impl_.constraint_id_.Mutable( GetArena());
}
inline std::string* ConstraintRuns::release_constraint_id() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  // @@protoc_insertion_point(field_release:operations_research.ConstraintRuns.constraint_id)
  return _impl_.constraint_id_.Release();
}
inline void ConstraintRuns::set_allocated_constraint_id(std::string* value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.constraint_id_.SetAllocated(value, GetArena());
  #ifdef PROTOBUF_FORCE_COPY_DEFAULT_STRING
        if (_impl_.constraint_id_.IsDefault()) {
          _impl_.constraint_id_.Set("", GetArena());
        }
  #endif  // PROTOBUF_FORCE_COPY_DEFAULT_STRING
  // @@protoc_insertion_point(field_set_allocated:operations_research.ConstraintRuns.constraint_id)
}

// repeated int64 initial_propagation_start_time = 2;
inline int ConstraintRuns::_internal_initial_propagation_start_time_size() const {
  return _internal_initial_propagation_start_time().size();
}
inline int ConstraintRuns::initial_propagation_start_time_size() const {
  return _internal_initial_propagation_start_time_size();
}
inline void ConstraintRuns::clear_initial_propagation_start_time() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.initial_propagation_start_time_.Clear();
}
inline ::int64_t ConstraintRuns::initial_propagation_start_time(int index) const {
  // @@protoc_insertion_point(field_get:operations_research.ConstraintRuns.initial_propagation_start_time)
  return _internal_initial_propagation_start_time().Get(index);
}
inline void ConstraintRuns::set_initial_propagation_start_time(int index, ::int64_t value) {
  _internal_mutable_initial_propagation_start_time()->Set(index, value);
  // @@protoc_insertion_point(field_set:operations_research.ConstraintRuns.initial_propagation_start_time)
}
inline void ConstraintRuns::add_initial_propagation_start_time(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _internal_mutable_initial_propagation_start_time()->Add(value);
  // @@protoc_insertion_point(field_add:operations_research.ConstraintRuns.initial_propagation_start_time)
}
inline const ::google::protobuf::RepeatedField<::int64_t>& ConstraintRuns::initial_propagation_start_time() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_list:operations_research.ConstraintRuns.initial_propagation_start_time)
  return _internal_initial_propagation_start_time();
}
inline ::google::protobuf::RepeatedField<::int64_t>* ConstraintRuns::mutable_initial_propagation_start_time()
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable_list:operations_research.ConstraintRuns.initial_propagation_start_time)
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  return _internal_mutable_initial_propagation_start_time();
}
inline const ::google::protobuf::RepeatedField<::int64_t>& ConstraintRuns::_internal_initial_propagation_start_time()
    const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.initial_propagation_start_time_;
}
inline ::google::protobuf::RepeatedField<::int64_t>* ConstraintRuns::_internal_mutable_initial_propagation_start_time() {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return &_impl_.initial_propagation_start_time_;
}

// repeated int64 initial_propagation_end_time = 3;
inline int ConstraintRuns::_internal_initial_propagation_end_time_size() const {
  return _internal_initial_propagation_end_time().size();
}
inline int ConstraintRuns::initial_propagation_end_time_size() const {
  return _internal_initial_propagation_end_time_size();
}
inline void ConstraintRuns::clear_initial_propagation_end_time() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.initial_propagation_end_time_.Clear();
}
inline ::int64_t ConstraintRuns::initial_propagation_end_time(int index) const {
  // @@protoc_insertion_point(field_get:operations_research.ConstraintRuns.initial_propagation_end_time)
  return _internal_initial_propagation_end_time().Get(index);
}
inline void ConstraintRuns::set_initial_propagation_end_time(int index, ::int64_t value) {
  _internal_mutable_initial_propagation_end_time()->Set(index, value);
  // @@protoc_insertion_point(field_set:operations_research.ConstraintRuns.initial_propagation_end_time)
}
inline void ConstraintRuns::add_initial_propagation_end_time(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _internal_mutable_initial_propagation_end_time()->Add(value);
  // @@protoc_insertion_point(field_add:operations_research.ConstraintRuns.initial_propagation_end_time)
}
inline const ::google::protobuf::RepeatedField<::int64_t>& ConstraintRuns::initial_propagation_end_time() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_list:operations_research.ConstraintRuns.initial_propagation_end_time)
  return _internal_initial_propagation_end_time();
}
inline ::google::protobuf::RepeatedField<::int64_t>* ConstraintRuns::mutable_initial_propagation_end_time()
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable_list:operations_research.ConstraintRuns.initial_propagation_end_time)
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  return _internal_mutable_initial_propagation_end_time();
}
inline const ::google::protobuf::RepeatedField<::int64_t>& ConstraintRuns::_internal_initial_propagation_end_time()
    const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.initial_propagation_end_time_;
}
inline ::google::protobuf::RepeatedField<::int64_t>* ConstraintRuns::_internal_mutable_initial_propagation_end_time() {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return &_impl_.initial_propagation_end_time_;
}

// int64 failures = 4;
inline void ConstraintRuns::clear_failures() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.failures_ = ::int64_t{0};
}
inline ::int64_t ConstraintRuns::failures() const {
  // @@protoc_insertion_point(field_get:operations_research.ConstraintRuns.failures)
  return _internal_failures();
}
inline void ConstraintRuns::set_failures(::int64_t value) {
  _internal_set_failures(value);
  // @@protoc_insertion_point(field_set:operations_research.ConstraintRuns.failures)
}
inline ::int64_t ConstraintRuns::_internal_failures() const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.failures_;
}
inline void ConstraintRuns::_internal_set_failures(::int64_t value) {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ;
  _impl_.failures_ = value;
}

// repeated .operations_research.DemonRuns demons = 5;
inline int ConstraintRuns::_internal_demons_size() const {
  return _internal_demons().size();
}
inline int ConstraintRuns::demons_size() const {
  return _internal_demons_size();
}
inline void ConstraintRuns::clear_demons() {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  _impl_.demons_.Clear();
}
inline ::operations_research::DemonRuns* ConstraintRuns::mutable_demons(int index)
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable:operations_research.ConstraintRuns.demons)
  return _internal_mutable_demons()->Mutable(index);
}
inline ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>* ConstraintRuns::mutable_demons()
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_mutable_list:operations_research.ConstraintRuns.demons)
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  return _internal_mutable_demons();
}
inline const ::operations_research::DemonRuns& ConstraintRuns::demons(int index) const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_get:operations_research.ConstraintRuns.demons)
  return _internal_demons().Get(index);
}
inline ::operations_research::DemonRuns* ConstraintRuns::add_demons() ABSL_ATTRIBUTE_LIFETIME_BOUND {
  PROTOBUF_TSAN_WRITE(&_impl_._tsan_detect_race);
  ::operations_research::DemonRuns* _add = _internal_mutable_demons()->Add();
  // @@protoc_insertion_point(field_add:operations_research.ConstraintRuns.demons)
  return _add;
}
inline const ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>& ConstraintRuns::demons() const
    ABSL_ATTRIBUTE_LIFETIME_BOUND {
  // @@protoc_insertion_point(field_list:operations_research.ConstraintRuns.demons)
  return _internal_demons();
}
inline const ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>&
ConstraintRuns::_internal_demons() const {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return _impl_.demons_;
}
inline ::google::protobuf::RepeatedPtrField<::operations_research::DemonRuns>*
ConstraintRuns::_internal_mutable_demons() {
  PROTOBUF_TSAN_READ(&_impl_._tsan_detect_race);
  return &_impl_.demons_;
}

#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif  // __GNUC__

// @@protoc_insertion_point(namespace_scope)
}  // namespace operations_research


// @@protoc_insertion_point(global_scope)

#include "google/protobuf/port_undef.inc"

#endif  // GOOGLE_PROTOBUF_INCLUDED_ortools_2fconstraint_5fsolver_2fdemon_5fprofiler_2eproto_2epb_2eh
