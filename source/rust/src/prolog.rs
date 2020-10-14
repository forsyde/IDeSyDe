pub mod prolog {

    trait Queryable {
        fn start() -> Result<bool, String>;
        pub fn query_goal<T>(database : Vec<Path>, goal: String) -> Result<HashMap<String, T>, String>;
        fn stop() ->  Result<bool, String>;
    }
}
